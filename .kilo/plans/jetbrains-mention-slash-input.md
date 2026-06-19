# JetBrains prompt input: undo, clean completion, not-found mention highlight

Improve the JetBrains plugin chat prompt input (mentions `@` and slash `/`). Three
independent fixes, all in the `frontend` module under
`packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/ui/`.

These are all Kilo-owned files (path contains `kilocode`/`client` and is not shared with
upstream opencode), so **no `kilocode_change` markers are needed**.

## Background (verified)

- The prompt editor is `PromptEditorTextField` → `SessionEditorTextField` → platform
  `com.intellij.ui.EditorTextField`, hosted in the chat tool window (`PromptPanel`).
- The mention/slash completion + highlight logic lives in
  `KiloPromptCompletionProvider`. Highlights are applied to the editor by
  `PromptPanel.syncHighlights()` via `markupModel` range highlighters.
- The CLI resolves *configured* references from prompt text; arbitrary file mentions are
  surfaced client-side. This change is **highlight + insertion only** — it does not change
  what gets attached/sent (`mentionParts`/`mentionFileParts` stay as-is).

---

## Issue 1 — Undo/redo (Cmd-Z) does nothing in the prompt

### Root cause

The platform `$Undo`/`$Redo` actions (`UndoRedoAction`) resolve the target editor from
`PlatformCoreDataKeys.FILE_EDITOR`. When focus is in the prompt editor, the focused
component is `EditorComponentImpl`, whose `uiDataSnapshot` provides `CommonDataKeys.EDITOR`
but **not** `FILE_EDITOR`. With a null `FileEditor`, `UndoDocumentUtil.getDocRefs(null)`
returns an empty list, so the undo manager has no document references and reports "nothing
to undo".

JetBrains' own `EditorTextField`-based inputs fix this by exposing a `FileEditor` wrapper.
See `platform/collaboration-tools/.../CodeReviewCommentTextFieldFactory.kt:114`
(`sink[PlatformCoreDataKeys.FILE_EDITOR] = TextEditorProvider.getInstance().getTextEditor(editor)`,
commented "required for undo/redo").

### Change

File: `session/ui/editor/SessionEditorTextField.kt`

In `uiDataSnapshot`, additionally expose a `TextEditor` wrapper of the current editor:

```kotlin
override fun uiDataSnapshot(sink: DataSink) {
    super.uiDataSnapshot(sink)
    ctx?.let { sink.set(PromptDataKeys.SEND, it) }
    getEditor(false)?.let { editor ->
        sink.set(
            PlatformCoreDataKeys.FILE_EDITOR,
            TextEditorProvider.getInstance().getTextEditor(editor),
        )
    }
}
```

New imports: `com.intellij.openapi.actionSystem.PlatformCoreDataKeys`,
`com.intellij.openapi.fileEditor.impl.text.TextEditorProvider`.

Notes:
- `TextEditorProvider.getTextEditor(editor)` caches the wrapper in the editor's user data,
  so this is cheap and stable across snapshots.
- This is the same wrapper platform `BasicUiDataRule` would synthesize; setting it
  explicitly guarantees undo/redo regardless of data-rule timing, and matches the
  collaboration-tools precedent.
- Applies to both prompt and the question custom-answer editor (both use
  `SessionEditorTextField`); both benefit from working undo.

---

## Issue 2 — Completing a mention mid-token leaves garbage

### Reproduction

Input `@backend/deploy⟨caret⟩-dev.sh`, accept the `backend/deploy-dev.sh` suggestion →
result is `@backend/deploy-dev.sh -dev.sh` (leftover suffix).

### Root cause

`KiloPromptCompletionProvider.replace()` (lines ~210-220) computes the replaced region end
as `ctx.tailOffset` — the end of the platform-inserted lookup string — instead of the end
of the whitespace-delimited token under the caret. The original suffix after the caret
(`-dev.sh`) is therefore left behind.

### Change

File: `session/ui/prompt/KiloPromptCompletionProvider.kt`

Compute `end` as the end of the full whitespace-delimited token (scan right from the token
start to the next whitespace), mirroring the existing left-scan used for `start` and the
boundary logic in `token()`:

```kotlin
private fun replace(ctx: InsertionContext, value: String, trim: Boolean, path: String? = null) {
    val text = ctx.document.text
    val offset = ctx.startOffset.coerceAtMost(text.length)
    val start = (offset - 1 downTo 0).firstOrNull { text[it].isWhitespace() }?.plus(1) ?: 0
    val end = (start until text.length).firstOrNull { text[it].isWhitespace() } ?: text.length
    val next = text.getOrNull(end)
    val insert = if (trim && next?.isWhitespace() == true) value.trimEnd() else value
    ctx.document.replaceString(start, end, insert)
    ctx.editor.caretModel.moveToOffset(start + insert.length)
    path?.let { paths.add(it); exists[it] = true } // exists seeding added in Issue 3
}
```

This fixes file mentions (`file`), special mentions (`special`/`@git-changes`), and server
slash commands (`server`), which all route through `replace`. End-of-token (normal)
completion behaves exactly as before.

Apply the same token-end computation to the "No matches" placeholder insert handler
(`info`, lines ~158-166) so accepting it mid-token also restores the prefix cleanly instead
of duplicating the suffix. Extract a tiny shared helper for the token end (e.g.
`tokenEnd(text, start)`) used by both `replace` and `info`.

Behavior holds for `\n` (Enter) and Tab acceptance — both run the same insert handler.

---

## Issue 3 — A mention whose file is not found is not highlighted (stays plain text)

Chosen approach (confirmed with user): **accurate** — validate each `@mention` against the
workspace via the backend and show a red error highlight only when the file truly does not
resolve. The token currently under the caret (being typed) is never flagged.

### Current behavior

`highlights()` only emits `MENTION` for completion-tracked `paths` (+ `git-changes`).
Every other `@token` (hand-typed or edited) renders as ordinary text, with no signal that
it will not resolve.

### Design

Add asynchronous, cached existence validation using the existing
`KiloWorkspaceService.files(directory, path)` RPC (returns matches or empty list; runs
off-EDT — the fake asserts `assertNotEdt("files")`).

#### `KiloPromptCompletionProvider.kt`

- Constructor: add `private val scope: CoroutineScope` (for launching validation).
- New state:
  - `private val exists = Collections.synchronizedMap(mutableMapOf<String, Boolean>())`
  - `private val pending = Collections.synchronizedSet(mutableSetOf<String>())`
- `clearMentions()`: also clear `exists` and `pending`.
- `replace(...)`: when a completed path is accepted, seed `exists[path] = true` (known-good,
  highlights instantly with no RPC) — see Issue 2 snippet.
- New `HighlightKind.INVALID` enum value.
- New private `mentionSpans(text): List<Span>` — whitespace-delimited tokens that start with
  `@` and have a non-empty value (returns `start`, `end`, `value`). Replaces the current
  substring/longest-first scan with direct token scanning (inherently non-overlapping).
- `highlights(text: String, caret: Int = -1): List<Highlight>` (add `caret` param, default
  `-1` keeps existing call sites/tests working):
  - Command highlight: unchanged.
  - For each mention span (`underCaret = caret in span.start..span.end`):
    - `value == "git-changes"` → `MENTION`
    - `value in paths || exists[value] == true` → `MENTION`
    - `underCaret` → skip (no highlight; being edited — avoids red flashing while typing)
    - `exists[value] == false` → `INVALID`
    - else (unknown / validation pending) → skip
- New `fun validate(text: String, caret: Int, onResolved: () -> Unit)`:
  - For each mention span where `value != "git-changes"`, `value !in paths`, not `underCaret`,
    `value !in exists`, `value !in pending`:
    - `pending.add(value)`
    - `scope.launch { val ok = runCatching { service.files(workspace.directory, value).isNotEmpty() }.getOrDefault(false); exists[value] = ok; pending.remove(value); onResolved() }`
  - `onResolved` is only invoked when an RPC actually completes (no launch → no callback →
    no refresh loop). EDT marshalling is the caller's responsibility.

#### `PromptPanel.kt`

- `syncHighlights()`:
  - Read `val caret = ed.caretModel.offset`.
  - Call `provider.validate(ed.document.text, caret) { ApplicationManager.getApplication().invokeLater { if (!project.isDisposed) refreshHighlights() } }`.
  - Pass `caret` to `provider.highlights(ed.document.text, caret)`.
  - Re-running `syncHighlights()` from the refresh callback is safe: all values are now
    cached or pending, so `validate` launches nothing and the callback chain terminates.
- Add error attributes key + mapping:
  - companion: `private val INVALID_KEY = CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES`
    (red "unresolved reference" attributes — semantically a not-found file reference).
  - `key(kind)`: add `INVALID -> INVALID_KEY` branch.
- New import: `com.intellij.openapi.editor.colors.CodeInsightColors`.

Notes / non-goals:
- Validation is best-effort and cached; files created/deleted after a check may show a stale
  state until re-typed. Acceptable for an input affordance.
- Attachment semantics (`mentionParts` / `mentionFileParts`, the tracked `paths` set used at
  send time) are **unchanged**. This issue is purely the pre-send highlight.

---

## Tests

All tests use `BasePlatformTestCase` (real Application + EDT), per package conventions.
Update the two `completion()` / provider constructions to pass the new `scope` argument:
- `KiloPromptCompletionProviderTest.setUp` (provider construction)
- `PromptPanelTest.completion()` helper
- `SessionUi` line ~336 passes `scope = cs`.

`FakeWorkspaceRpcApi`: add a `var fileResolver: ((String) -> List<WorkspaceFileDto>)? = null`
and make `files()` return `fileResolver?.invoke(path) ?: fileMatches`, so a single test can
make one path resolve and another not. (Kilo test file — no markers.)

### `KiloPromptCompletionProviderTest.kt`

- **Mid-token clean insert (new):** configure `@backend/deploy⟨caret⟩-dev.sh`, search returns
  `backend/deploy-dev.sh`, accept via `myFixture.type('\n')`, assert document is
  `"@backend/deploy-dev.sh "` and `mentionPaths()` contains `backend/deploy-dev.sh`.
- **Mid-token clean insert with trailing content (new):** `@backend/deploy⟨caret⟩-dev.sh tail`
  → `"@backend/deploy-dev.sh tail"` (trailing space trimmed against existing whitespace).
- **Not-found mention → INVALID (new):** `fileResolver` returns empty for `unknownPath`; call
  `validate("see @unknownPath", caret = -1) { ... }`, `waitFor` until resolved, then assert
  `highlights("see @unknownPath", -1)` contains `Highlight(4, 16, INVALID)`.
- **Existing hand-typed mention → MENTION (new):** `fileResolver` returns a match for
  `src/x.kt`; after `validate`/`waitFor`, `highlights("see @src/x.kt")` → `MENTION`.
- **Mention under caret is not flagged (new):** with `exists` empty, `highlights("@nope", caret = 5)`
  is empty (skipped while being typed).
- **Update** `test highlights ignore untracked mentions`: keep asserting the *synchronous*
  result is empty (pending), and add the async resolution assertion above. Rename to reflect
  "pending → resolves to error".
- Existing tracked/`git-changes`/longest-first highlight tests keep passing unchanged.

### `PromptPanelTest.kt`

- **Undo/redo (new):** build `PromptPanel` with completion, `realize`, get
  `field.getEditor(false)`. Insert text inside a `WriteCommandAction`/`CommandProcessor`
  command, then resolve `PlatformCoreDataKeys.FILE_EDITOR` from the editor content
  component's data context (`DataManager.getInstance().getDataContext(...)`), assert it is
  non-null and wraps the prompt document, and that
  `UndoManager.getInstance(project).undo(fileEditor)` reverts the text (and `redo` reapplies).
  This exercises the real `uiDataSnapshot` contribution end-to-end.
- **Error highlight (new):** set `fileResolver`/`fileMatches` so `@missing` resolves to empty;
  set `field.text = "@missing"`, pump events + `waitFor`, assert a highlighter spans
  `@missing` with `CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES`.
- **Update** `test prompt editor highlights validated commands and mentions` (currently
  asserts `@unknown` has no highlight): make it deterministic under the new async validation —
  either drive `@unknown` to resolve-not-found and assert the INVALID span, or keep it pending
  and assert no synchronous highlight. Keep the `/new` + `@git-changes` assertions.
- Existing clear/bounded highlighter tests: confirm still ≤ 2 for `/new @git-changes`.

---

## Verification

From `packages/kilo-jetbrains/`:

- `./gradlew typecheck` (or `bun run typecheck`)
- `./gradlew test` (targeted: the two prompt test classes above)

Requires Java 21 (only diagnose Java if Gradle fails with a Java error). Also do a manual
`./gradlew runIde` smoke check: Cmd-Z/Cmd-Shift-Z in the prompt, mid-token completion, and a
not-found `@mention` turning red.

## Touched files

- `frontend/.../session/ui/editor/SessionEditorTextField.kt` — expose `FILE_EDITOR` (undo).
- `frontend/.../session/ui/prompt/KiloPromptCompletionProvider.kt` — token-end insert fix;
  async existence validation + `INVALID` highlight kind; `scope` ctor param.
- `frontend/.../session/ui/prompt/PromptPanel.kt` — pass caret, drive validation, map
  `INVALID` to error attributes.
- `frontend/.../session/SessionUi.kt` — pass `scope = cs` to the provider.
- `frontend/src/test/.../session/ui/prompt/KiloPromptCompletionProviderTest.kt` — tests.
- `frontend/src/test/.../session/ui/PromptPanelTest.kt` — tests.
- `frontend/src/test/.../testing/FakeWorkspaceRpcApi.kt` — `fileResolver` hook for tests.
