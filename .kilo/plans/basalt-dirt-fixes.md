# Plan: Address `basalt-dirt` review findings (JetBrains slash/mention completion)

Companion to `.kilo/plans/basalt-dirt-review.md`. All work is in `packages/kilo-jetbrains/`
(Kilo-owned — no `kilocode_change` markers, no shared opencode edits).

Decision captured: **remove `@terminal` until implemented** (keep `@git-changes`, which is fully wired).

Execution order below is also the recommended priority. Each task lists files + intent; keep edits
minimal and follow the JetBrains `AGENTS.md` (single-word names, `@RequiresEdt` where applicable,
no test-only production methods, prefer pure helpers for testability).

---

## Task 1 — Remove the half-wired `@terminal` mention

Goal: no dead UX. `@terminal` must not be suggested, highlighted, or leave stub RPC plumbing.

**Frontend**
- `session/ui/prompt/KiloPromptCompletionProvider.kt`
  - In `mention()`: delete the `if ("terminal".startsWith(prefix …) && search.terminal)` block.
  - In `highlights()`: change the special set from `setOf("terminal", "git-changes")` to
    `setOf("git-changes")`.
- `session/SessionUi.kt`
  - In `mentionParts()`: delete the `@terminal` block (and its `runBlocking { workspaces.terminalOutput(...) }`).
- `app/KiloWorkspaceService.kt`: delete `terminalOutput(...)`.
- `resources/messages/KiloBundle.properties`: delete `prompt.mention.terminal`.

**Shared**
- `rpc/KiloWorkspaceRpcApi.kt`: delete `terminalOutput(directory)`.
- `rpc/dto/WorkspaceFileDto.kt`: remove the unused `terminal: Boolean = false` field from `FileSearchResultDto`.

**Backend**
- `rpc/KiloWorkspaceRpcApiImpl.kt`: delete the `terminalOutput()` override. `searchFiles()` already
  returns no `terminal` flag — confirm it still compiles after the DTO field removal.

**Tests / fakes to update (compile breakers)**
- `testing/FakeWorkspaceRpcApi.kt`: remove `var terminalOutput` + the `terminalOutput()` override.
- `KiloPromptCompletionProviderTest.kt`:
  - `test mention completion includes matching special items`: drop `terminal = true` and the
    terminal assertion; keep `git-changes`.
  - `test highlights special mentions without tracked paths`: assert only the `@git-changes`
    highlight (recompute offsets for a text containing just `@git-changes`).
- `PromptPanelTest.kt`: in the three highlight tests, replace `@terminal` usages with `@git-changes`
  (or a tracked file mention) so the highlight/clear/bounded assertions still exercise real spans.

---

## Task 2 — Make the completion provider the single owner of command knowledge (de-dup)

Goal: eliminate the triplicated client-command-name list and the duplicated `/name` parsing.

- `session/ui/prompt/KiloPromptCompletionProvider.kt`
  - Add `fun serverCommand(text: String): Pair<String, String>?` that reuses `clientNames()` and
    `workspace.state.value.commands` (same rules as today: leading `/`, non-blank name, not a client
    name, must match a known server command; returns `name to args`). This centralizes the parsing
    already echoed in `highlights()`.
- `session/SessionUi.kt`
  - Promote the completion provider to a field (e.g. `private lateinit var completion` assigned in the
    existing init block, or store it) so `sendPrompt()` can reach it.
  - Replace `SessionUi.serverCommand(text)` with a call to `completion.serverCommand(text)`.
  - **Delete `SessionUi.clientCommandNames()`** entirely.

Result: client-action names exist once (in `slashActions()` → provider `actions` → `clientNames()`),
and `/name` routing logic lives once in the provider.

---

## Task 3 — Extract pure mention-assembly helper + fix frontend `runBlocking`

Goal: testable submit-side assembly and AGENTS-compliant blocking.

- New pure helper (top-level functions or a small object in `session/ui/prompt/`, e.g.
  `PromptMentionParts.kt`):
  - `fun mentionFileParts(text: String, paths: Set<String>, directory: String): List<PromptPartDto>`
    — the current tracked-path → `file` part logic (path resolve, `toUri()`, `source(...)`), pure.
  - `fun gitChangesPart(text: String, diff: String?): PromptPartDto?` — builds the `data:` URL part
    when `@git-changes` is present and `diff` is non-blank (current `dataPart`/`source` logic), pure.
  - Move `dataPart()` and `source()` here (or keep private and expose only the two functions).
- `session/SessionUi.kt` `mentionParts()` becomes a thin wrapper:
  - `mentionFileParts(text, paths, workspace.directory)` +
  - `gitChangesPart(text, runBlockingCancellable { workspaces.gitChanges(workspace.directory) })`.
  - **Replace `runBlocking` with `com.intellij.openapi.progress.runBlockingCancellable`** (still on the
    pooled thread from `PromptPanel.submit`; cancellation/progress aware). Remove the `kotlinx.coroutines.runBlocking` import.
- While here, tighten the `@git-changes` match to require a word boundary (end-of-token), so
  `@git-changes-foo` does not match. Apply the same boundary rule already used in `highlights()`.

---

## Task 4 — Tests for `SessionController.command()`

Goal: cover the new ~50-line controller method (mirrors `prompt()`).

- `testing/FakeSessionRpcApi.kt`: add `var commandThrows: Exception? = null`; in `command(...)`
  throw it before recording, so error paths are testable (mirrors `enhanceThrows`). The existing
  `commands` list is the assertion target.
- New `controller/CommandLifecycleTest.kt` (extends `SessionControllerTestBase`), modeled on
  `PromptLifecycleTest`:
  - **New session**: `controller()` + ready app/workspace with a server command named e.g. `deploy`;
    `edt { m.command("deploy", "prod") }`; `flush()`; assert one `rpc.commands` entry with
    `command="deploy"`, `arguments="prod"`; assert a session was created and events subscribed.
  - **Existing session**: from `prompted()`, run `m.command(...)`; assert it reuses the session id and
    does not create a new one.
  - **Telemetry**: assert `appRpc.telemetry` has `"Conversation Send Clicked"` with `source="command"`
    and `"Conversation Message"` with `source="command"`.
  - **Error path**: set `rpc.commandThrows`; assert `m.model.state is SessionState.Error` and a
    `"Session Error"` telemetry event with `context="command"`.

---

## Task 5 — Tests for submit-side assembly (uses the Task 3 helper)

Goal: cover routing + part building without test-only production methods.

- New `session/ui/prompt/PromptMentionPartsTest.kt` (plain unit test; `BasePlatformTestCase` only if
  needed for `PromptPartDto`):
  - `mentionFileParts`: tracked path present in text → one `file` part with absolute `file://` url,
    `filename`, and `source.path`/`source.text` offsets; untracked path → no part; absolute path in
    `paths` kept absolute.
  - `gitChangesPart`: `@git-changes` present + non-blank diff → one `data:text/plain` part with
    URL-encoded content (`+` → `%20`) and `source.uri="git-changes"`; null/blank diff → no part;
    `@git-changes-foo` (no boundary) → no part.
- `serverCommand` routing: add a case to `KiloPromptCompletionProviderTest`:
  - With a workspace command `deploy` and client action `new`: `serverCommand("/deploy x")` →
    `"deploy" to "x"`; `serverCommand("/new")` → null (client action); `serverCommand("hi /deploy")`
    → null (not leading); `serverCommand("/unknown")` → null.

---

## Task 6 — Add the missing feature changeset

- New `.changeset/jetbrains-prompt-completion.md`, bump `minor`:
  - `"@kilocode/kilo-jetbrains": minor`
  - User-facing summary, e.g.: "Add `/` slash commands and `@` file/git-changes mentions to the
    JetBrains chat prompt with native completion." Imperative, no implementation detail.
- Leave the two existing `patch` changesets (`jetbrains-file-mention-parity`,
  `jetbrains-stable-mention-completion`) as-is — they describe follow-up fixes.

---

## Task 7 — Minor cleanups

- `session/SessionUi.kt`: add `import ai.kilocode.client.plugin.KiloBundle` and replace the 8
  fully-qualified `ai.kilocode.client.plugin.KiloBundle.message(...)` calls in `slashActions()` with
  `KiloBundle.message(...)`.
- `session/ui/prompt/PromptPanel.kt` `showCompletion()`: add a short comment noting it relies on
  IntelliJ impl/internal completion APIs (`CodeCompletionHandlerBase`, `LookupImpl`,
  `LookupPresentation`, `LookupPositionStrategy`) and may need revisiting on platform upgrades.
  (Keep `@Suppress("UnstableApiUsage")` as-is.)

---

## Out of scope (documented limitation, no change)

- Manually typed `@path` mentions (no popup selection) still won't attach a file part — only
  popup-inserted paths are tracked. This matches the original design decision; revisit only if
  parity with VS Code's text-scan approach is requested.

---

## Verification

From `packages/kilo-jetbrains/` (requires Java 21 — check `java -version`; install via
`sdk install java 21-tem && sdk use java 21-tem` if missing):

- `./gradlew typecheck` — compiles all Kotlin incl. terminal removal across shared/backend/frontend.
- `./gradlew test` (or targeted: `./gradlew test --tests '*KiloPromptCompletionProviderTest*'`
  `--tests '*CommandLifecycleTest*'` `--tests '*PromptMentionPartsTest*'` `--tests '*PromptPanelTest*'`).
- Sanity: `bunx changeset status` (or confirm the new changeset parses) from repo root.

Done when: `@terminal` is fully gone, `clientCommandNames()` is deleted, `command()` and
mention-assembly have tests, frontend uses `runBlockingCancellable`, and the `minor` changeset exists,
with typecheck + tests green.
