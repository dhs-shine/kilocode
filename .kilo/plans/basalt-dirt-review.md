# Branch review: `basalt-dirt` — JetBrains slash/mention completion

Scope: 5 commits vs `main`, all under `packages/kilo-jetbrains/` (+ `.changeset/`, `.kilo/plans/`).
Feature: `/` slash commands + `@` file/terminal/git mentions in the JetBrains prompt, via native
IntelliJ completion, plus hiding synthetic file-mention payloads in the transcript.

Reviewed against the requested focus areas. Findings are ordered by severity within each area.

---

## 1. Did we change unnecessary opencode code? — Clean

- Every changed source file lives under `packages/kilo-jetbrains/` (a fully Kilo-owned package).
  No edits to `packages/opencode/src/` or any other shared upstream path.
- Per the fork rules, `kilo-jetbrains` needs **no `kilocode_change` markers** (the whole package is
  a Kilo addition), so none were added — correct.
- New files follow the existing package convention (`ai.kilocode.client`, `ai.kilocode.backend`,
  `ai.kilocode.rpc`), consistent with surrounding code.

No action needed.

---

## 2. Code duplication — One real issue, a couple of minor overlaps

### 2.1 (Should fix) Client slash-command names are listed twice
- `SessionUi.slashActions()` (SessionUi.kt:~590) is the source of truth: 8 actions, each with a `name`.
- `SessionUi.clientCommandNames()` (SessionUi.kt:638) hardcodes the **same 8 names** again as a
  `setOf(...)`, used by `serverCommand()` to avoid routing a client action to the server.
- `KiloPromptCompletionProvider.clientNames()` already derives the same set from `actions`.
- Result: three representations of the same list. Adding/removing a slash action silently breaks
  `serverCommand()` routing unless `clientCommandNames()` is also edited.
- Fix: drop `clientCommandNames()` and derive from the single source — e.g. have `serverCommand()`
  consult `completion.clientNames()` (or `slashActions().mapTo(...) { it.name }`).

### 2.2 (Minor) Command-detection logic duplicated across highlight vs submit
- `KiloPromptCompletionProvider.highlights()` and `SessionUi.serverCommand()` independently parse a
  leading `/name` and match it against `workspace.state.value.commands`. Not identical (one highlights,
  one routes), but the "is this a known command name" predicate could be shared.

### 2.3 (Not duplication, noted) `PartSourceDto` build/parse
- `KiloCliDataParser.sourceJson()`/`parseSource()` (serialize/deserialize) vs `SessionUi.source()`
  (construct from UI state) operate at different layers — acceptable, not duplication.

---

## 3. Test coverage — Good for model/UI, two real gaps

### Well covered
- `KiloCliDataParserTest`: synthetic flag + `source` round-trips for `parseChatEvent`, `parseMessages`,
  `buildPromptJson`, `buildCommandJson`.
- `SessionModelTest`: hidden synthetic user text (update/append/loadHistory), file-attachment `source`.
- `SessionUiUpdateTest`: hiding synthetic read payload + text attachment card; source-less and
  image attachments still render.
- `PromptPanelTest`: highlight spans for validated command/mention, clear removes highlighters,
  **highlighters stay bounded across 50 edits** (matches the AGENTS retained-Swing/leak expectation).
- `KiloPromptCompletionProviderTest`: fuzzy results un-filtered, prefix cache reuse + reset, special
  items, highlight rules.
- Fakes extended for `command`, `searchFiles`, `terminalOutput`, `gitChanges`.

### Gaps
- **(Should fix) `SessionController.command()` is untested.** This new ~50-line method mirrors
  `prompt()` (lazy session creation, telemetry capture, error → `SessionState.Error`) but has **no**
  `SessionControllerTestBase` test. The `FakeSessionRpcApi.commands` tracking list that was added for
  this is currently **unused** by any assertion — dead test scaffolding confirming the gap. AGENTS
  explicitly wants controller logic exercised via `SessionControllerTestBase`.
- **(Should fix) Submit-side assembly in `SessionUi` is untested.** `serverCommand()` routing
  (prompt vs command), `mentionParts()` (file-part URL/path resolution, `@terminal`/`@git-changes`
  data-URL building), and `dataPart()` encoding have no tests. The transcript-hiding side is tested,
  but the send side is not.
- **(Acceptable) Backend `searchFiles`/`search`/`gitChanges` untested** — `GotoFileModel` needs a real
  indexed project and `gitChanges` shells out to `git`; hard to unit test. Reasonable to skip, but
  worth a note.

---

## 4. Conformance to JetBrains AGENTS.md — Mostly conformant; a few flags

### Conformant
- New non-light service methods are added to existing RPC interfaces in `shared/` with `suspend`,
  `@Serializable` DTOs (`FileSearchResultDto`, `PartSourceDto`, `PartSourceTextDto`) — matches the
  RPC/payload rules.
- RPC implemented in `backend`, consumed from `frontend` via `KiloWorkspaceService`/`KiloSessionService`.
- Highlight colors use theme-derived `TextAttributesKey`s (`DefaultLanguageHighlighterColors.METADATA`
  / `KEYWORD`) rather than hardcoded `Color` — matches Theme-Derived Colors.
- UI strings added to `KiloBundle.properties`.
- `SessionModel` mutations annotated `@RequiresEdt`; new hidden-synthetic logic resets in both
  `loadHistory()` and `clear()` — matches the "reset in both" rule for new model state.
- Single-word naming honored in the provider (`paths`, `cached`, `search`, `token`, `slash`, `mention`,
  `file`, `replace`).

### Flags
- **(Should fix) `runBlocking` on the frontend in `SessionUi.mentionParts()`** (SessionUi.kt:677, 690).
  AGENTS: "Call RPC from frontend coroutines only … do not paper over this with blocking wrappers";
  `runBlocking` is the most-discouraged form. It runs on a pooled thread (off-EDT, via
  `PromptPanel.submit`'s `executeOnPooledThread`), so it is not an EDT violation, but at minimum it
  should be `runBlockingCancellable` (cancellation/progress aware), and ideally the submit path should
  expose a coroutine seam so the two sequential RPCs aren't blocking a pooled thread.
- **(Acceptable, document) `runBlockingCancellable` in `KiloPromptCompletionProvider.search()`.**
  AGENTS marks it "not recommended", but `TextCompletionProvider.fillCompletionVariants` is a
  synchronous platform API on the completion background thread with no suspend seam. The plan justified
  this; it is the standard pattern for that extension point. Keep, but it's the right call to flag.
- **(Risk, warn) Reliance on impl/internal completion APIs in `PromptPanel.showCompletion()`:**
  `CodeCompletionHandlerBase.createHandler(...)`, `LookupImpl`, `LookupPresentation`,
  `LookupPositionStrategy`. AGENTS: avoid internal APIs; experimental ones are OK if the user is warned.
  These are needed to force the popup position; acceptable but should be called out as a platform-upgrade
  fragility point.
- **(Minor) Verbose fully-qualified `ai.kilocode.client.plugin.KiloBundle.message(...)`** repeated 8×
  in `slashActions()` — import `KiloBundle` instead.

---

## 5. Other issues

- **(Should fix) `@terminal` is half-wired / effectively dead.**
  - Backend `KiloWorkspaceRpcApiImpl.terminalOutput()` is a hardcoded `return null` stub, and
    `searchFiles()` never sets `terminal = true` (defaults false).
  - So `@terminal` is **never suggested** in completion (`search.terminal` always false) and **never
    attaches content** (`terminalOutput` null), yet `KiloPromptCompletionProvider.highlights()`
    unconditionally highlights `@terminal` as if it were a valid mention. This is misleading UX +
    dead RPC plumbing.
  - Decide: either implement `terminalOutput` (and set the `terminal` flag), or remove `@terminal`
    from `highlights()`/`special()` and the stub RPC until implemented. `@git-changes` is fully wired,
    so the asymmetry stands out.
- **(Should fix) Missing/under-scoped changeset for the feature.** The branch adds only two `patch`
  changesets (`jetbrains-file-mention-parity`, `jetbrains-stable-mention-completion`). The headline
  user-facing feature — `feat(jetbrains): add prompt slash and mention completion` — has **no
  changeset**, and a new feature should be `minor`, not `patch`. Add a `minor` changeset describing the
  new `/` and `@` completion from the user's perspective.
- **(Minor) Manually typed mentions don't attach files.** `mentionParts()` only emits file parts for
  paths the popup inserted into `completion.paths`. Typing `@src/Foo.kt` by hand (no popup selection)
  highlights but attaches nothing. Matches the plan's "track inserted paths" decision, but is a parity
  gap worth recording.
- **(Minor) `@terminal`/`@git-changes` substring matching** in `mentionParts()` uses
  `text.indexOf("@terminal")`, which also matches `@terminalfoo`. Low impact.

---

## Suggested follow-up priority

1. Implement or remove `@terminal` (avoid shipping misleading dead UX).
2. Add `SessionController.command()` tests (and assert `FakeSessionRpcApi.commands`).
3. De-duplicate `clientCommandNames()` → derive from `slashActions()`/`clientNames()`.
4. Add a `minor` changeset for the slash/mention feature.
5. Replace frontend `runBlocking` with `runBlockingCancellable` (or a coroutine seam) in `mentionParts()`.
6. Add submit-side tests for `serverCommand()`/`mentionParts()`.
7. Minor: import `KiloBundle`; note the internal-completion-API risk in a comment.

No blocking correctness defects found; the above are quality/maintainability and one
incomplete-feature (`@terminal`) concern.
