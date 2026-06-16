# JetBrains: slash commands + file mentions via native IntelliJ completion

## Goal

Reach VS Code parity for the prompt input in the JetBrains plugin:

- `/` slash commands (built-in client actions **and** server/custom commands a.k.a. workflows)
- `@` file/folder mentions

Render both with **IntelliJ's native completion lookup popup** (not a custom Swing popup), since the
prompt is already a real `EditorTextField` over `PlainTextFileType`.

File-mention search must use **JetBrains' own index API** (`FilenameIndex`). When the project index is
not built yet (dumb mode), the **mention completion only** must show a clear "indexing" message — the
rest of the plugin (including slash commands) must not wait or be blocked.

## Key facts established during research

- Prompt input: `session/ui/prompt/PromptPanel.kt` → `PromptEditorTextField` → `SessionEditorTextField : EditorTextField(project, PlainTextFileType.INSTANCE)`. No slash/mention handling today.
- Platform registers `TextCompletionContributor` for `language="TEXT"` (`LangExtensions.xml:1243`). It is a **no-op unless** a `TextCompletionProvider` is installed on that specific document's PSI file (`TextCompletionUtil.COMPLETING_TEXT_FIELD_KEY`). So enabling completion on the Kilo prompt does **not** affect other plain-text editors.
- Install path: either subclass `TextFieldWithCompletion` (which wires the provider + autopopup via `DocumentWithCompletionCreator`), or call `TextCompletionUtil.installProvider(psiFile, provider, autoPopup=true)` on the prompt document's PSI file.
- Autopopup: `AutoPopupController.getInstance(project).scheduleAutoPopup(editor)` (public, `analysis-impl`). For invocationCount 0 the contributor only fills when `AUTO_POPUP_KEY=true`.
- `TextCompletionUtil.getProvider` calls `DumbService.isUsableInCurrentContext(provider)`: a provider that is **not** `DumbAware` is skipped during indexing → no completion at all. Make the provider `DumbAware` so slash commands keep working while indexing.
- Slash-command data already exists: `KiloWorkspaceStateDto.commands: List<CommandDto>`, exposed via `Workspace.state` / `KiloWorkspaceService`.
- File search: existing `KiloWorkspaceRpcApi.files(directory, path)` is a **literal path resolver**, not fuzzy search. Need a new fuzzy-search RPC.
- Split-mode constraint: completion runs in the **frontend** process which has no project index; `FilenameIndex` lives on the **backend**. File search therefore must go over RPC, and the dumb-mode flag is a **backend** condition surfaced in the RPC result.
- Server commands run via CLI `POST /session/{id}/command` (SDK `session.command`). JetBrains has no command-execution path yet (`KiloSessionRpcApi` has `prompt` but no `command`).
- Prompt parts: `PromptPartDto(type, text?, mime?, url?, filename?)`. File mention → `type="file", url="file://abs"` (reference, not base64; base64 is only used for pasted media in `PromptAttachment.part()`).
- `DumbService.isDumb` (`platform/core-api/.../DumbService.kt:66`), `FilenameIndex.processAllFileNames(...)` + `FilenameIndex.getVirtualFilesByName(name, scope)`, `NameUtil.buildMatcher("*"+query)` → `MinusculeMatcher`.
- Backend project resolution helper already exists: `KiloWorkspaceRpcApiImpl.project(path: Path): Project?` and `file(...)`/`clean(...)`.

## Architecture decision

One frontend `TextCompletionProvider` handles **both** `/` and `@`. Slash data is local (workspace
state). Mention data comes from a new backend `searchFiles` RPC backed by `FilenameIndex`, with an
`indexing` flag. The provider is `DumbAware` so it always runs; mention branch renders an indexing
notice when the backend reports the index isn't ready. Slash never depends on the index.

```
PromptPanel editor (frontend, PlainText)
  └─ DocumentListener: on '/' or '@' typed → AutoPopupController.scheduleAutoPopup(editor)
        └─ TextCompletionContributor (platform, language=TEXT)
              └─ KiloPromptCompletionProvider (DumbAware)
                    ├─ getPrefix → detect SLASH vs MENTION vs none(null)
                    ├─ SLASH → client actions + Workspace.state.commands  (no index)
                    └─ MENTION → KiloWorkspaceService.searchFiles(dir,q) ──RPC──▶ backend
                                   FileSearchResultDto(indexing, files)
                                   indexing=true → show "Indexing… file mentions soon"
                                   else → file/folder lookup elements
```

---

## Phase 0 — Wire native completion into the prompt editor

Files: `session/ui/editor/SessionEditorTextField.kt`, `session/ui/prompt/PromptEditorTextField.kt`,
`session/ui/prompt/PromptPanel.kt`, `session/SessionUi.kt`.

1. Add a `TextCompletionProvider?` parameter to `SessionEditorTextField` (default null so the
   question custom-answer editor stays plain). When non-null, install it on the document's PSI file.
   Prefer the documented path: build the document via
   `LanguageTextField.createDocument(value, PlainTextLanguage.INSTANCE, project, TextCompletionUtil.DocumentWithCompletionCreator(provider, autoPopup=true))` and pass it to the `EditorTextField` constructor; or call `TextCompletionUtil.installProvider(PsiDocumentManager.getInstance(project).getPsiFile(document), provider, true)` after construction. Keep the existing `uiDataSnapshot`/`PromptDataKeys.SEND` behavior.
2. `PromptEditorTextField` forwards the provider to `SessionEditorTextField`.
3. `PromptPanel` constructor gains a `completion: TextCompletionProvider` param (built in `SessionUi`,
   see Phase 1/2). Keep all existing `addSettingsProvider` styling/soft-wrap/height logic — it still
   applies because `TextFieldWithCompletion`/`EditorTextField` is the same component family.
4. In `PromptPanel`'s existing `DocumentListener.documentChanged`, after a single-char insertion of
   `/` (only when it is the first non-space char of the document) or `@`, call
   `AutoPopupController.getInstance(project).scheduleAutoPopup(editorComponent)`. Guard with
   `project.isDisposed`.
5. `SessionUi.kt:328` constructs `PromptPanel` — pass the provider here, where `project`, `workspace`,
   `controller`, and `app` are in scope.

Acceptance: typing `/` or `@` in the prompt opens the native lookup; other plain-text editors are
unaffected.

---

## Phase 1 — Slash commands

New file: `session/ui/prompt/PromptSlashCompletion.kt` (helper that produces `LookupElement`s for the
slash branch), consumed by the shared provider in Phase 2's `fillCompletionVariants`.

### 1a. Client-action commands (mirror `useSlashCommand.ts`)

Define a small list of built-ins, each: name, description, hints (aliases), and an `action: () -> Unit`.
Map to existing JetBrains capabilities:

| Command | Action target (existing) |
|---|---|
| `new` | start new session (SessionManager / new-session flow) |
| `sessions` (`history`,`resume`) | open history (`HistoryPanel`/`SessionManager`) |
| `models` | open `PromptPanel.model` (`ModelPicker`) |
| `agents` (`modes`) | open `PromptPanel.mode` (`ModePicker`) |
| `variant` (`reasoning`) | open `PromptPanel.reasoning` (`ReasoningPicker`) |
| `compact` (`smol`) | `controller.compact(...)` |
| `settings` | open Kilo settings |
| `help` | open `https://kilo.ai/docs` |

- Each picker likely needs a public `open()`/`showPopup()` hook if it doesn't already expose one; add a
  minimal method on the picker rather than reaching into internals.
- `export`/`remote` from VS Code are **dropped** for JetBrains (no equivalent capability today). Do not
  invent features.
- Lookup element: `LookupElementBuilder.create(name).withPresentableText("/"+name)
  .withTailText("  "+description, true).withLookupStrings(hints)
  .withIcon(...)`. `InsertHandler`: clear the document (`context.document.setText("")`) and run
  `action()` on the EDT via `ApplicationManager.getApplication().invokeLater`. Mark with a badge tail
  ("custom"/"skill"/"mcp") analogous to VS Code where source info is available.

### 1b. Server/custom commands (workflows)

- Source: `workspace.state.value.commands` (`List<CommandDto>` already populated). Filter out names
  that collide with client actions. Build lookup elements; `InsertHandler` inserts `"/"+name+" "` and
  positions the caret after it (do **not** clear / run an action).
- Execution at submit time (mirrors VS Code `prompt-input-utils` + `sendCommand`):
  - In `PromptPanel.submit`/`SessionUi.sendPrompt`, detect a leading `/<name>` where `<name>` is a known
    server command; split into `name` + `args` (rest of the text). If matched, route to a new
    controller command path instead of a normal prompt.
  - Add `SessionController.command(name: String, args: String, files: List<PromptPartDto>)` mirroring
    `prompt(...)`: lazily create session if needed, then call a new session RPC.
  - New RPC `KiloSessionRpcApi.command(id, directory, name, args, model: ModelSelectionDto?)` →
    backend `KiloSessionRpcApiImpl.command(...)` → CLI `POST /session/{id}/command`
    (mirror `chat.prompt`; reuse the existing CLI HTTP client used by chat). Add a `CommandDto`-free
    request payload as needed.
  - Optimistic display + event flow reuse the existing prompt pipeline.

Acceptance: `/` shows built-ins + workflows; selecting a built-in runs its action and clears input;
selecting a workflow inserts `/name `; submitting `/name args` runs the workflow.

---

## Phase 2 — File mentions via `FilenameIndex`, with dumb-mode handling

### 2a. Shared DTO + RPC

File: `shared/.../rpc/dto/WorkspaceFileDto.kt` (add a result wrapper) or a new
`FileSearchResultDto.kt`:

```kotlin
@Serializable
data class FileSearchResultDto(
    val indexing: Boolean = false,
    val files: List<WorkspaceFileDto> = emptyList(),
    val terminal: Boolean = false,   // an active terminal exists → offer @terminal
    val git: Boolean = false,        // project under git → offer @git-changes
)
```

File: `shared/.../rpc/KiloWorkspaceRpcApi.kt` — add:

```kotlin
/** Fuzzy file/folder search via the IDE index. indexing=true means the index isn't ready yet. */
suspend fun searchFiles(directory: String, query: String, limit: Int = 50): FileSearchResultDto
```

### 2b. Backend impl (JetBrains API)

File: `backend/.../rpc/KiloWorkspaceRpcApiImpl.kt` — add `searchFiles`:

```kotlin
override suspend fun searchFiles(directory: String, query: String, limit: Int): FileSearchResultDto {
    val base = file(clean(directory) ?: directory) ?: return FileSearchResultDto()
    val project = project(base) ?: return FileSearchResultDto()
    if (DumbService.getInstance(project).isDumb) return FileSearchResultDto(indexing = true)
    return try {
        readAction { search(project, base, query, limit) }   // com.intellij.openapi.application.readAction
    } catch (e: IndexNotReadyException) {
        // indexing started mid-search — surface the same not-ready state, do not block
        FileSearchResultDto(indexing = true)
    }
}
```

`search(project, base, query, limit)` (read action):
- `val matcher = NameUtil.buildMatcher("*").build()` when query blank, else `NameUtil.buildMatcher("*$query").build()` (`MinusculeMatcher`, case-insensitive substring/camel match).
- Collect candidate names: `FilenameIndex.processAllFileNames({ name -> if (matcher.matches(name)) names.add(name); names.size < CAP }, GlobalSearchScope.projectScope(project), null)` (cap candidate names, e.g. 2000, to bound work).
- For each matched name: `FilenameIndex.getVirtualFilesByName(name, GlobalSearchScope.projectScope(project))` → for each `vf`, compute path relative to `base` (skip if not under `base`), build `WorkspaceFileDto(path = relative, name = vf.name, directory = vf.isDirectory)`.
- Sort by `matcher.matchingDegree(name)` desc, then shorter path; take `limit`.
- Return `FileSearchResultDto(indexing = false, files = …)`.

Notes:
- Do **not** call `DumbService.waitForSmartMode` / `runReadActionInSmartMode` — those block. We explicitly want the non-blocking `indexing=true` short-circuit.
- Perf escape hatch (only if `processAllFileNames` proves too heavy on huge projects): switch to
  `GotoFileModel` / `ChooseByNameModelEx` which the IDE's "Go to File" uses. Keep `FilenameIndex` as the
  default; note this alternative in code comments only if needed.
- Imports: `com.intellij.psi.search.FilenameIndex`, `com.intellij.psi.search.GlobalSearchScope`,
  `com.intellij.openapi.project.DumbService`, `com.intellij.openapi.project.IndexNotReadyException`,
  `com.intellij.psi.codeStyle.NameUtil`, `com.intellij.openapi.application.readAction`.

### 2c. Frontend service

File: `frontend/.../app/KiloWorkspaceService.kt` — add:

```kotlin
suspend fun searchFiles(directory: String, query: String, limit: Int = 50): FileSearchResultDto =
    try { call { searchFiles(directory, query, limit) } }
    catch (e: Exception) { LOG.warn("file search failed dir=$directory q=$query", e); FileSearchResultDto() }
```

### 2d. Mention branch in the provider

In `fillCompletionVariants` MENTION branch (runs on the completion **background** thread, off EDT):
- `val result = runBlockingCancellable { service.searchFiles(directory, prefix, 50) }`
  (`com.intellij.openapi.progress.runBlockingCancellable`). Background-thread blocking is acceptable here;
  never do this on the EDT.
- If `result.indexing`:
  - `resultSet.addLookupAdvertisement(KiloBundle.message("prompt.mention.indexing"))`
  - add one non-inserting info element:
    `LookupElementBuilder.create("").withPresentableText(message).withIcon(AllIcons.General.Information)`
    with an `InsertHandler` that no-ops (or removes itself). Optionally
    `resultSet.restartCompletionWhenNothingMatches()` and `DumbService.getInstance(project).runWhenSmartMode { scheduleAutoPopup }` (frontend project) so the popup refreshes once indexing finishes.
- Else build a `LookupElement` per file/folder: present text `@`+path, tail = parent dir, file-type icon
  (`FileTypeManager`/`AllIcons.Nodes.Folder`), `withLookupString(path)`; `InsertHandler` replaces the
  `@query` token with `@path ` (see Phase 3).

Directory used for `searchFiles` = `workspace.directory` (available in `SessionUi`); pass it into the
provider at construction.

Acceptance: `@` shows fuzzy file/folder matches from the IDE index; during initial indexing the
mention popup shows an "indexing" notice while slash commands and the rest of the UI work normally.

### 2e. Special mentions: `@terminal` and `@git-changes`

Mirror VS Code (`terminal-context-utils.ts`, `git-changes-context-utils.ts`). Each is a single
synthetic mention that, on submit, embeds gathered text as a data-URL `file` part:
`PromptPartDto(type="file", mime="text/plain", url="data:text/plain;charset=utf-8,<encoded>", filename=...)`
(`terminal-output.txt` / `git-changes.txt`). The `@terminal` / `@git-changes` tokens stay in the text.

Completion candidates (in the MENTION branch, **before** file results, filtered by prefix like VS Code's
`getTerminalMentionResult`/`getGitChangesMentionResult`):
- `@terminal` — show only when a terminal is available (terminal tool window / `org.jetbrains.plugins.terminal` present and has a session).
- `@git-changes` — show only when the project root is under VCS/git.
These two require **no index**, so offer them even while `indexing=true` (do not gate behind dumb mode).

Backend content RPCs (new on `KiloWorkspaceRpcApi`, since content depends on IDE/project state):
- `suspend fun terminalOutput(directory: String): String?` — read the active terminal widget scrollback.
  Use `org.jetbrains.plugins.terminal.TerminalView.getInstance(project)` → active
  `ShellTerminalWidget`/content; best-effort `getText()` of the terminal buffer. Return `null` if the
  terminal plugin/tool window is unavailable so the frontend can hide the mention. Guard the
  terminal-plugin dependency so the plugin still loads if it's absent.
- `suspend fun gitChanges(directory: String): String?` — current uncommitted changes as a unified diff.
  Prefer VCS API (`ChangeListManager.getInstance(project).allChanges` → render patch) or shell
  `git diff` (+ `git diff --staged`) via the existing CLI/process util scoped to `directory`. Return
  `null`/empty when not a git repo.

Availability flags for the completion popup: expose lightweight checks
(`terminalAvailable(directory)` / `gitAvailable(directory)`) — either as separate RPCs or folded into
`FileSearchResultDto` (e.g. add `terminal: Boolean`, `git: Boolean` flags returned by `searchFiles`).
Prefer folding into `searchFiles` to avoid extra round-trips; the provider then knows which special
mentions to offer alongside file results (and during indexing, `searchFiles` can still return the flags
with `indexing=true` and empty `files`).

Submit-time assembly (Phase 3): when `@terminal` / `@git-changes` is present in the text, call the
corresponding backend RPC, encode the returned text, and append the data-URL `file` part. Skip if the
RPC returns null/empty.

Acceptance: `@` offers `@terminal` (when a terminal exists) and `@git-changes` (when under git); both are
offered even while indexing; submitting attaches the gathered terminal/diff text as a file part.

---

## Phase 3 — Mention insert + attachment building on submit

- Track inserted mention paths. Simplest robust approach: on submit, re-derive mentions from the text
  by scanning for `@<path>` tokens whose `<path>` matches a path we offered/inserted. Mirror VS Code's
  `buildFileAttachments`: keep a `Set<String>` of inserted paths in the provider/panel; the mention
  `InsertHandler` adds the path to that set.
- `InsertHandler` for a file: compute the `@query` token range before the caret, replace with
  `@<path> ` (trailing space unless next char is whitespace), and register `<path>`.
- In `PromptPanel.submit`, for each tracked path still present as `@<path>` in the text, append a
  `PromptPartDto(type="file", mime="text/plain", url="file://<abs>", filename=<name>)` where
  `<abs> = directory + "/" + path` (or the path if already absolute). Keep the `@path` text in the
  message (matches VS Code).
- If the text contains `@terminal` / `@git-changes`, call `workspace.terminalOutput(dir)` /
  `workspace.gitChanges(dir)`; when non-empty, append a data-URL `file` part (Phase 2e). These RPCs run
  off the EDT inside the existing pooled-thread submit path (`PromptPanel.submit` already does
  `executeOnPooledThread`).
- Optional polish (defer): atomic backspace deletion of a whole `@path`, arrow-key skip, selection
  snapping — the editor already handles most caret behavior; only add if needed.

Acceptance: submitting a prompt with `@src/Foo.kt` sends a `file` part referencing that file plus the
text.

---

## i18n / resources

- Add bundle keys: `prompt.mention.indexing`, slash command descriptions, badges
  (`prompt.slash.badge.custom/skill/mcp`) in the frontend `*.properties` (mirror VS Code `en.ts`
  keys). Keep all user-visible strings in properties.

## Testing (extend `SessionControllerTestBase` / `BasePlatformTestCase`)

- `KiloPromptCompletionProvider` prefix detection: SLASH only at text start, MENTION after whitespace,
  null otherwise (pure unit test on `getPrefix`).
- Slash: client actions present + invoke their action and clear text; server commands inserted as
  `/name `; submit routes `/name args` to `controller.command`.
- Mentions: with a real indexed test project, `searchFiles` returns expected relative paths; build
  lookup elements; insert handler produces `@path ` and a `file` part on submit.
- **Dumb-mode**: drive `searchFiles` while `DumbService` is dumb (or stub the backend RPC to return
  `indexing=true` via `FakeSessionRpcApi`-style fake for the workspace RPC) and assert the mention
  branch yields the indexing advertisement/info element and **no** file elements, while the slash
  branch still returns commands. Use `DumbModeTestUtils` / `BasePlatformTestCase` dumb helpers for the
  backend search unit test.
- Native completion smoke test: install provider on an `EditorTextField`, type `/` and `@`, assert the
  platform builds the expected lookup items (use `CompletionAutoPopupTestCase`/`myFixture` patterns where
  feasible; otherwise test the provider directly via a synthetic `CompletionParameters`).
- No new EDT/threading violations; `searchFiles` runs in a read action; RPC never called on EDT.

## File-change checklist

Shared:
- `shared/.../rpc/dto/` add `FileSearchResultDto` (with `indexing`/`terminal`/`git` flags).
- `shared/.../rpc/KiloWorkspaceRpcApi.kt` add `searchFiles`, `terminalOutput`, `gitChanges`.
- `shared/.../rpc/KiloSessionRpcApi.kt` add `command` (Phase 1b).

Backend:
- `backend/.../rpc/KiloWorkspaceRpcApiImpl.kt` add `searchFiles` (+ `search` helper), `terminalOutput`
  (terminal plugin, guarded), `gitChanges` (VCS API or `git diff`), imports.
- `backend/.../rpc/KiloSessionRpcApiImpl.kt` add `command` → CLI `/session/{id}/command` (Phase 1b).
- `backend/build.gradle.kts`: add an **optional** dependency on `org.jetbrains.plugins.terminal` for
  terminal scrollback; keep it soft so the plugin loads without it (reflective/`PluginManager` guard or
  `<depends optional>` style). Confirm against AGENTS "bundle third-party libs" rule — platform plugin
  deps are declared via the IntelliJ Platform Gradle plugin, not bundled.

Frontend:
- `frontend/.../app/KiloWorkspaceService.kt` add `searchFiles`, `terminalOutput`, `gitChanges`.
- `frontend/.../session/ui/editor/SessionEditorTextField.kt` accept + install provider.
- `frontend/.../session/ui/prompt/PromptEditorTextField.kt` forward provider.
- `frontend/.../session/ui/prompt/PromptPanel.kt` accept provider param; autopopup trigger in doc
  listener; mention→file-part assembly in `submit`; server-command submit routing.
- `frontend/.../session/ui/prompt/KiloPromptCompletionProvider.kt` (new): `TextCompletionProvider, DumbAware`.
- `frontend/.../session/ui/prompt/PromptSlashCompletion.kt` (new, optional split): slash lookup builders.
- `frontend/.../session/SessionUi.kt` construct provider with `project`/`workspace`/`controller`/`app`
  and pass to `PromptPanel`; wire client-action targets (picker `open()` hooks).
- `frontend/.../session/controller/SessionController.kt` add `command(name,args,files)` (Phase 1b).
- Picker components (`ModePicker`/`ModelPicker`/`ReasoningPicker`) expose a public `open()` if missing.
- Frontend `*.properties` add bundle keys.

## Scope decisions (confirmed)

1. **Server-command execution (Phase 1b): included now** — new `KiloSessionRpcApi.command` + CLI
   `/session/{id}/command` wiring is part of this plan.
2. **Special mentions: include `@terminal` + `@git-changes`** (Phase 2e), gated on availability.
3. **`/export` and `/remote`: dropped** on JetBrains (no equivalent capability).

## Suggested execution order

Phase 0 → 1a (client actions) → 2a–2d (files + dumb-mode) → 3 (file attachments) → 1b (workflow
execution) → 2e (terminal/git-changes mentions). Each phase is independently testable.

## Risks

- `FilenameIndex.processAllFileNames` cost on very large projects — mitigated by candidate cap + the
  `GotoFileModel` escape hatch.
- `runBlockingCancellable` inside completion fill: safe on the background completion thread, but keep
  the RPC fast and cancellable; never run on EDT.
- Provider must be `DumbAware` or completion is fully suppressed during indexing.
- `@terminal` scrollback depends on the optional terminal plugin and its (semi-internal) widget API —
  keep it best-effort and warn if it relies on an experimental/internal IntelliJ API; hide the mention
  when unavailable rather than failing.
- `gitChanges` via shelling `git diff` must use the project-spawn process util (Windows `windowsHide`)
  and be scoped to `directory`; large diffs should be size-capped before embedding as a data URL.
