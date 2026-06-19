# JetBrains prompt: syntax-highlight @mentions and /commands

## Goal

Visually distinguish, inside the JetBrains chat prompt editor, the two token kinds the
completion system already understands:

- `@mention` tokens (file/folder paths, plus the special `@terminal` / `@git-changes`)
- `/command` tokens (client slash actions + server commands), only at the very start of the prompt

Highlighting is **semantic / validated** (the chosen mode): only mentions that resolve to a
real tracked path or special mention, and only `/commands` that match a known command, get
colored. Bare `@foo` / `/foo` that aren't real are left as plain text — same rule the VS Code
webview uses (`buildHighlightSegments` only paints tracked paths).

## Current state (verified)

The completion feature is already implemented and committed (`3150da9986` +
`7fd80d0af9` + `0c2a7c1c5b`). Relevant pieces:

- `KiloPromptCompletionProvider` (`frontend/.../session/ui/prompt/KiloPromptCompletionProvider.kt`)
  is a `TextCompletionProvider` that:
  - owns the token grammar in `token(text, offset)` (`:158-165`): SLASH = leading `/` with the
    text before it blank and no space; MENTION = `@` with no space.
  - tracks inserted file-mention paths in a synchronized `paths` set; exposes `mentionPaths()`
    (`:41`) and `clearMentions()` (`:43`).
  - knows client action names via `clientNames()` (`:48`) and reads server commands from
    `workspace.state.value.commands` (`:75-77`).
  - inserts `@terminal` / `@git-changes` via `special()` (`:127-131`) — note these are **not**
    added to `paths`.
- The editor is a real `EditorEx` behind `EditorTextField`:
  `PromptPanel.editor` = `PromptEditorTextField(project, this, completion)` →
  `SessionEditorTextField` (`PlainTextFileType`, completion-backed document).
  The live editor is reachable via `editor.getEditor(false): EditorEx?` (lazy, non-null once shown).
- `PromptPanel` already has the hooks we need:
  - a `DocumentListener.documentChanged` (`:222-229`) that runs on the EDT on every edit.
  - `applyStyle(style)` (`:330-339`) re-applies the editor color scheme on theme change.
  - `clear()` (`:342-348`) resets text + mentions.
  - `completion: KiloPromptCompletionProvider?` field (`:91`) — null for the question
    custom-answer editor, so highlighting naturally scopes to the prompt only.
- There is a proven in-repo highlighting precedent: `MdViewHybrid.applyTerm/applyShell`
  (`ui/md/hybrid/MdViewHybrid.kt:641-690`) + `MdShellHighlight` (`ui/md/hybrid/MdShellHighlight.kt`)
  apply ranges via `editor.markupModel.addRangeHighlighter(TextAttributesKey, start, end,
  HighlighterLayer.SYNTAX + 1, HighlighterTargetArea.EXACT_RANGE)`.

There is **no** existing highlighting in the prompt editor today — confirmed no `markupModel`,
`RangeHighlighter`, or `TextAttributes` usage in `prompt/` or `editor/`.

## Options for how to highlight (with recommendation)

### Option A — MarkupModel range highlighters, recomputed on edit (RECOMMENDED)

Recompute the validated token ranges on each document change and apply them to the live
editor's `markupModel` with theme-aware `TextAttributesKey`s, mirroring `MdViewHybrid`.

- Pros: matches the existing in-repo pattern; entirely kilo-owned frontend code (no shared
  files, no upstream merge surface, no extension-point registration); theme-aware; trivially
  supports the **semantic** rule because the validation data (tracked paths, known commands)
  is right there in the completion provider; recompute cost is negligible for short prompt text.
- Cons: highlights are decorative only (no PSI); we recompute the whole (small) buffer per edit.

### Option B — Custom lexer-based `EditorHighlighter` via `EditorEx.setHighlighter`

Write a small `Lexer` + `SyntaxHighlighter` for `@`/`/` tokens and set it on the editor in
`addSettingsProvider`. The platform then maintains incremental highlighting.

- Pros: platform-managed incremental highlighting; integrates with the editor color scheme.
- Cons: a hand-written `Lexer` is more code; crucially a lexer only sees **characters**, not the
  tracked-paths set or the loaded command list, so it cannot do the **semantic / validated**
  highlighting the user asked for (it would color any `@word`/`/word`). It also has to coexist
  with the completion-backed document setup. Heavier for a worse fit.

### Option C — Full custom `Language` + `FileType` + `Annotator` (PSI)

Register a dedicated language with a parser and an `Annotator` for semantic coloring.

- Pros: the "proper" platform way; PSI unlocks future features.
- Cons: large effort and registration surface for two token kinds; conflicts with the existing
  plain-text + completion document; overkill.

**Recommendation: Option A.** It is the smallest change, reuses a tested repo pattern, keeps
everything in kilo-owned files, and is the only option that cleanly expresses semantic
validation. The rest of this plan implements Option A.

## Design (Option A)

### 1. Compute highlight ranges in the completion provider

The provider already owns the grammar and the validation data, so the range computation belongs
there (single source of truth, no duplicated parsing).

Add to `KiloPromptCompletionProvider`:

```kotlin
enum class HighlightKind { MENTION, COMMAND }
data class Highlight(val start: Int, val end: Int, val kind: HighlightKind)

/** Validated highlight ranges for the whole prompt text. EDT-safe, pure over [text]. */
fun highlights(text: String): List<Highlight>
```

Rules inside `highlights(text)`:

- **Command** (at most one, at the start): if `text` starts with `/`, take the run up to the
  first whitespace as `name`; if `name` is in `clientNames()` or in
  `workspace.state.value.commands.map { it.name }`, emit `Highlight(0, 1 + name.length, COMMAND)`.
  Mirrors the SLASH branch of `token()` and `SessionUi.serverCommand`.
- **Mentions** (anywhere): for each candidate set member — the tracked `mentionPaths()` plus the
  literals `terminal` and `git-changes` — find every `@<value>` occurrence whose end is at a
  whitespace or end-of-text, and emit `Highlight(idx, idx + 1 + value.length, MENTION)`. Match
  **longest value first** so `@src/a.tsx` isn't shadowed by `@src/a.ts` (same precaution as
  webview `syncMentionedPaths`/`findMentionRange`). Skip overlaps already covered.

This keeps `@terminal`/`@git-changes` highlighted even though they aren't in `paths`, by checking
the two literals explicitly.

### 2. Apply ranges to the live editor in `PromptPanel`

- Add a field to track our own highlighters so we never disturb anything else on the editor:
  `private val highlighters = mutableListOf<RangeHighlighter>()`.
- Add `@RequiresEdt private fun syncHighlights()`:
  - `val provider = completion ?: return` (no provider → question editor → no highlighting).
  - `val ed = editor.getEditor(false) ?: return` (editor not realized yet).
  - Remove our previous highlighters: `highlighters.forEach(ed.markupModel::removeHighlighter); highlighters.clear()`.
  - For each `provider.highlights(ed.document.text)`, clamp to `document.textLength`, then
    `ed.markupModel.addRangeHighlighter(keyFor(kind), start, end, HighlighterLayer.SYNTAX + 1,
    HighlighterTargetArea.EXACT_RANGE)` and store the returned highlighter.
  - `keyFor(MENTION)` / `keyFor(COMMAND)` map to `TextAttributesKey`s (see step 3).
- Call `syncHighlights()` from:
  - the existing `documentChanged` listener (`:222-229`), after `triggerCompletion(e)` — covers
    typing, completion inserts (which mutate the document and update `paths`), paste, and clear.
  - the `addSettingsProvider` block (after the editor is configured) so the first realization of
    the editor paints existing draft text.
  - `applyStyle(...)` is not strictly required because `TextAttributesKey` highlighters recolor
    with the scheme automatically and the editor instance is retained, but add a
    `syncHighlights()` there too for safety on Look-and-Feel/scheme changes.
  - when the server command list arrives: the `commands` list can load after the user has typed
    `/foo`. Re-run `syncHighlights()` when workspace state updates. `PromptPanel` doesn't observe
    workspace state directly today; the simplest wiring is to expose a `PromptPanel.refreshHighlights()`
    (EDT) and call it from `SessionUi` where it already reacts to `SessionControllerEvent.WorkspaceReady`
    (`SessionUi.kt:377`). Keep this a no-op-cheap call.

### 3. Colors — theme-aware `TextAttributesKey`

Per the JetBrains UI guidelines, prefer existing platform keys (as `MdShellHighlight` does with
`DefaultLanguageHighlighterColors.*`). Use:

- `MENTION` → `DefaultLanguageHighlighterColors.METADATA` (renders like `@annotation` accents —
  a natural fit for `@mentions`).
- `COMMAND` → `DefaultLanguageHighlighterColors.KEYWORD` (distinct, keyword-like).

These require **no** registration and recolor with the active editor scheme automatically.

Optional enhancement (not required for first cut): register two custom keys
(`KILO_PROMPT_MENTION`, `KILO_PROMPT_COMMAND`) with the above as fallbacks plus a color-settings
page, so users can recolor them. Defer unless we want user-configurable colors.

### 4. Edge cases

- Token at caret while typing: highlight only validated tokens, so a half-typed `@de` stays plain
  until completion inserts a real path (which adds it to `paths` and triggers a recompute). This
  matches the webview's tracked-path behavior and avoids flicker.
- Overlapping/duplicate paths: longest-first matching + skip-covered avoids double highlighting.
- Empty/`null` editor: guard with `getEditor(false)?` (already the pattern used at `:335`,`:467`).
- Performance: prompt text is short; full rescan per keystroke is fine. No debounce needed; if it
  ever matters, gate on "text contains '@' or starts with '/'".

## Files to change (all kilo-owned — no shared/upstream files, no `kilocode_change` markers needed)

- `frontend/.../session/ui/prompt/KiloPromptCompletionProvider.kt`
  - add `HighlightKind`, `Highlight`, and `highlights(text)`.
- `frontend/.../session/ui/prompt/PromptPanel.kt`
  - add `highlighters` list, `syncHighlights()`, call sites (document listener,
    `addSettingsProvider`, `applyStyle`), and a public `refreshHighlights()` for the
    command-loaded case.
- `frontend/.../session/SessionUi.kt`
  - call `prompt.refreshHighlights()` from the `WorkspaceReady` handler (`:377`).

No new bundle strings, RPC, DTOs, backend, or extension-point registration.

## Testing

Follow the existing JetBrains pattern (`BasePlatformTestCase`, no mocks). There is already
`KiloPromptCompletionProviderTest` using `myFixture.configureByText` + `TextCompletionUtil.installProvider`.

1. Provider unit tests for `highlights(text)` (pure function, no editor needed):
   - `/new ...` at start with `new` a known client action → one COMMAND range over `/new`.
   - `/bogus ...` → no COMMAND range.
   - `/foo` not at start (e.g. `hi /foo`) → no range.
   - `@src/a.ts` present in `paths` → MENTION range; `@src/a.tsx` longest-first not shadowed.
   - `@terminal` / `@git-changes` literals → MENTION ranges without being in `paths`.
   - `@unknownPath` not tracked → no range.
2. `PromptPanelTest` (real EDT) — type text, drain EDT, assert the editor's markup model contains
   the expected number of highlighters with the expected ranges/keys; assert `clear()` removes
   them; assert no highlighter growth across repeated edits (bounded count), per the
   stress/leak-test guidance for editor-backed UI.

## Verification

From `packages/kilo-jetbrains/` (requires Java 21 — check `java -version` first):

- `./gradlew typecheck`
- `./gradlew test` (or the smallest relevant test task for the frontend module)

Optionally `./gradlew runIde` to eyeball mention/command coloring in the prompt.

## Out of scope / notes

- Completion, file search, `@terminal`/`@git-changes` attachments, server-command execution, and
  send-time file parts already exist — this plan only adds visual highlighting on top.
- `@terminal` content is currently a backend stub (`KiloWorkspaceRpcApiImpl.terminalOutput` returns
  `null`) and `searchFiles` never advertises `terminal=true`; that's a separate completion-content
  gap, independent of highlighting. The literal `@terminal` will still be highlighted if typed.
- Keep all logic in the kilo-owned `frontend` files above to preserve the small upstream diff.
