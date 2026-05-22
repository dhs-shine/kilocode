# Broken Pipeline Chains Review — PR #10507 (OpenCode v1.14.41)

## Methodology

1. Pulled the full PR diff (`gh pr diff 10507 --repo Kilo-Org/kilocode`, 14k lines).
2. Located every `kilocode_change` marker in the diff (~80 hits across ~30 files) and the surrounding upstream context.
3. For each Kilo-touched chain, walked upstream → downstream:
   - producers / setters / event emitters
   - intermediate routers, schemas, projectors, sync stores
   - downstream consumers (UI props, projectors, handlers, tests).
4. Cross-checked the post-merge tree (`grep` of `packages/opencode/src` and `packages/kilo-vscode/src`) for stale references to symbols/events/types renamed by upstream.

Focus areas: workspace warp/restore rename, SyncEvent ownerID system, ACP `defaultModel` chain, `Session.get` error-channel migration, `Modelv2.Ref` payload migration in `SessionEvent.ModelSwitched`, TUI sync-v2 ordering flip, prompt/index `lastUserMessage` chain, ACP session cleanup, and kilocode_change marker integrity.

## Findings

Each finding is rated as **Confirmed broken**, **Likely broken**, or **Worth verifying** (default when in doubt).

### 1. Worth verifying — `SyncEvent.claim()` ownerID never reaches the remote `/sync/replay` handler

- `packages/opencode/src/control-plane/workspace.ts:622` calls `SyncEvent.claim(input.sessionID, input.workspaceID ?? Instance.project.id)` during a session warp. After this call, `sync/index.ts:91-93` rejects any subsequent `replay`/`replayAll` whose `options.ownerID` does not equal the claimed owner.
- The remote endpoints invoked during warp (`packages/opencode/src/server/routes/instance/httpapi/handlers/sync.ts:50` and the legacy Hono route `packages/opencode/src/server/routes/instance/sync.ts:103`) call `sync.replayAll(events)` / `SyncEvent.use.replayAll(events)` with **no `ownerID`** option.
- This is the new upstream "session steal" mechanism and ours is unchanged from upstream, but since Kilo TUI/Agent Manager users may exercise warp differently than the upstream desktop app (e.g. local-only workspaces), confirm that:
  - The local-only warp path that calls `SyncEvent.claim` then later replays locally still functions — local replay also goes through `replay()` / `replayAll()` without ownerID.
  - The bookkeeping in our worktree-based Agent Manager flows still work after a session warp, because subsequent local emissions of session events for the same `sessionID` will be **silently dropped** unless the caller threads `ownerID`.
- Although mainly an upstream concern, confirm Kilo CLI flows (e.g. `kilo run` re-using sessions across worktrees) do not regress.

### 2. Worth verifying — `Session.get` now returns `Effect.Effect<Info, NotFound>` (was throwing as defect)

- `packages/opencode/src/session/session.ts:7882` returns `Effect.fail(new NotFoundError(...))` instead of `throw new NotFoundError(...)`. The `Interface` types now expose `NotFound` in the error channel for `get`, `fork`, and `remove`.
- All upstream callers in `session/prompt.ts`, `session/revert.ts` were updated with `.pipe(Effect.orDie)` to preserve old behavior.
- However `packages/opencode/src/tool/task.ts:70` still does `const parent = yield* sessions.get(ctx.sessionID)` **without `Effect.orDie`**, so the tool's effect now carries a `NotFound` error in its type. This is upstream code (no kilocode_change) but is part of the Kilo task tool runtime — confirm `bun run typecheck` is green, and that any Kilo callers of `tool/task.ts` don't rely on `NotFound` being a defect.
- Kilo wrappers (`packages/opencode/src/kilocode/permission/allow-everything.ts`, `kilo-sessions/*`, `tool/recall.ts`, `kilocode/plan-followup.ts`) all use the **promise** facade `Session.get(...).catch(...)`, which is unchanged — no impact at runtime, but verify the storage `NotFoundError` continues to be the same class as the new schema-tagged variant referenced in `cli/cmd/tui/component/dialog-session-list.tsx` etc.

### 3. Worth verifying — `SessionEvent.ModelSwitched` payload shape change

- The schema flipped from flat `{ id, providerID, variant }` to nested `{ model: Modelv2.Ref }` (`packages/opencode/src/v2/session-event.ts:50`). All in-tree producers (`session/prompt.ts:1022`, `session/processor.ts:511`, `v2/session.ts:279`) and consumers (`session/projectors-next.ts:152`, `v2/session-message-updater.ts:108`, `v2/session-message.ts:26`) were updated.
- However `Modelv2.VariantID.make(input.variant ?? "default")` is now used unconditionally — if any historical persisted `assistant.variant` round-trips through `processor.ts` with a value that does not parse as a `VariantID` brand, it could throw. Since the brand has no validator (just `Schema.brand`) this should be safe, but worth a unit test pass on `bun test ./test/v2/session-message-updater.test.ts`.
- Tests in `test/v2/session-message-updater.test.ts:11842` and `test/tool/task.test.ts` now construct `Modelv2.ID.make/ProviderID.make/VariantID.make` — confirm Kilo-only test fixtures (under `test/kilocode/`) do not still build `ModelSwitched` payloads in the old flat shape. (Quick grep showed none.)

### 4. Worth verifying — TUI `sync-v2` newest-first ordering invalidates downstream `findLast`/reverse logic

- `packages/opencode/src/cli/cmd/tui/context/sync-v2.tsx` was switched from `push` + `findLastIndex` → `unshift` + `findIndex`, i.e. `sync.data.messages[sessionID]` is now stored newest-first.
- The Kilo-specific block at `packages/opencode/src/cli/cmd/tui/component/prompt/index.tsx:373-400` (sync local agent/model on newest user msg) reads `sync.data.message[sessionID]` (singular `message`, the v1 store) — it is **not** affected.
- However the non-Kilo block `feature-plugins/system/session-v2.tsx:43` does `messages().toReversed()` which used to convert "oldest-first" → "newest-first"; with the new unshift behavior `toReversed()` now produces oldest-first ordering. Confirm this was an intentional upstream UI change and that Kilo-specific subagent footers / sidebar / dialogs that inspect `sync.data.messages` still work.

### 5. Worth verifying — workspace `extra` made optional, but `CreateInput.extra` defaults to `null`

- `packages/opencode/src/control-plane/workspace.ts:5202` made `CreateInput.extra` `Schema.optional`, and the create call now spreads `{ ...input, extra: input.extra ?? null }`. Confirm Kilo adapters (any `kilocode/control-plane/...` or VSCode Agent Manager backend) don't pass undefined when null is required, and that consumers of `WorkspaceInfo.extra` still tolerate null vs. their previous default.

### 6. Worth verifying — ACP `defaultModel` no longer throws "no providers configured" message

- `packages/opencode/src/acp/agent.ts:1727` previously threw `"no model available: no providers are configured and no default model is set"`; upstream replaced it with the generic `"No models available"`. The Kilo error-handling path that surfaces this to users (toast / VSCode webview) may rely on the original wording. Search for callers that match against the old string before promoting this PR.

### 7. Worth verifying — `dialog-workspace-create` API rename (`restoreWorkspaceSession` / `openWorkspaceSession` / `DialogWorkspaceCreate` → `warpWorkspaceSession` / `openWorkspaceSelect` / `DialogWorkspaceSelect`)

- All in-tree call sites were renamed (`dialog-session-list.tsx`, `prompt/index.tsx`). A repo-wide grep for the old names returns zero hits in source, but confirm:
  - Any Kilo-only commands/agents (`.kilo/command/*.md`) or webview UI that historically referenced the old slash command (e.g. `/restore`) now use `/warp`. Slash is now `slash: { name: "warp" }` (`prompt/index.tsx:3860`).
  - The kilocode_change end markers in `dialog-session-list.tsx` still bracket the right code; the upstream-removed "ctrl+w new workspace" entry was outside the kilocode block, so the surrounding `// kilocode_change end` at `dialog-session-list.tsx:289` correctly closes the renamed/rewired section.

### 8. Worth verifying — `Workspace.Event.Restore` removed without replacement event

- The `workspace.restore` BusEvent was deleted entirely (`control-plane/workspace.ts:5193`) along with the `total/step` progress payload. No in-tree subscribers remained, but anything outside this repo (Kilo VS Code extension, JetBrains plugin, Agent Manager UI, telemetry pipeline) that listened to `workspace.restore` via SSE/event API will now silently never see progress updates. Search the cloud / VSCode codebases before merging.

### 9. Worth verifying — `SessionRestoreInput` / `SessionRestoreHttpError` renamed to `SessionWarpInput` / `SessionWarpHttpError`

- These error classes are exported from `control-plane/workspace.ts`. No Kilo consumers found in this repo. Confirm `@kilocode/sdk` regeneration and any error-name string comparisons (e.g. `result?.error?.name === "VcsApplyError"` in `dialog-workspace-create.tsx:3293`) are still aligned.

### 10. Worth verifying — `editorContextHover` / `dismissEditorContext` removed

- Prompt's hover-to-dismiss editor-context label was replaced with a static label driven by `editor.labelState()` (`packages/opencode/src/cli/cmd/tui/context/editor.ts:380`). The functions `dismissEditorContext` and `editorSelectionKey` (formerly used by the Kilo-touched submit path) are no longer called from `prompt/index.tsx:3960`. No kilocode_change in this area was removed, but confirm any Kilo overrides or stories under `packages/kilo-vscode/webview-ui/` / `packages/kilo-ui/` don't import `dismissEditorContext`.

### 11. Worth verifying — `processor.ts` step-failed event coerces `error.type` to literal `"unknown"`

- Upstream tightened `Step.Failed.error` to `UnknownError` (`session-event.ts:8569`) with `type: Schema.Literal("unknown")`. `session/processor.ts:7680` now hardcodes `type: "unknown"`, dropping the prior `error.name`. Any Kilo-side telemetry / analytics that branched on `step.failed.error.type` will lose distinguishability. Spot-check `packages/kilo-telemetry/` and the Suggestion/Question Kilo pipelines.

### 12. Worth verifying — `task.ts` cost propagation refactor: `Effect.ensuring` placement

- `packages/opencode/src/tool/task.ts:8064-8081` was changed to `(costBefore, exit) => Effect.gen { if (Exit.hasInterrupts(exit)) yield cancel }.pipe(Effect.ensuring(<old propagate logic>))`.
- The kilocode_change "snapshot child cost" / "propagate subagent cost delta" markers still wrap the right blocks, but the `cancel` effect (formerly synchronous) is now `Effect.Effect<void>`; `ops.cancel` returning `Effect.Effect<void>` is honored. However the new code yields `cancel` only on interrupt — confirm that on **non-interrupt completion** the cancel chain is not still expected to run elsewhere (it isn't called any other way), and that the `(_)` swallowing in test stub `cancel: () => Effect.void` matches the new signature.

### 13. Worth verifying — `Modelv2.VariantID.make("default")` fallback collides with `availableVariants` filter

- `packages/opencode/src/acp/agent.ts:formatModelIdWithVariant` (line ~2580 of diff) now picks `variant ?? availableVariants[0]` and `buildConfigOptions` similarly defaults to `DEFAULT_VARIANT_VALUE`. Previously when no variant was selected, the function returned the bare `provider/model` id; now it always appends a variant suffix. Any Kilo-side parser (e.g. `Provider.parseModel`, kilo-gateway) that historically received `provider/model` without trailing `/variant` will now receive a 3-segment id. Verify Kilo's gateway routing (`packages/kilo-gateway/`) tolerates the new format.

### 14. Worth verifying — `Workspace` layer now requires `Vcs.Service` + `Instance` + `InstanceStore`

- New imports at `control-plane/workspace.ts:5146-5171`. `defaultLayer` was extended (line ~5697 in diff). Anywhere Kilo code constructs `Workspace.layer` for tests/scripts without the `Vcs.defaultLayer` / `InstanceStore.defaultLayer` / `InstanceBootstrap.defaultLayer` will fail at startup. The test `httpapi-instance-context.test.ts` was updated, but spot-check Kilo-only tests under `test/kilocode/` and any standalone scripts.

## Summary

No outright **confirmed broken** chain was found. The upstream merge is mechanically sound — every renamed symbol in shared code has its callers updated in the same patch, and Kilo-specific files (`packages/opencode/src/kilocode/`, `packages/opencode/test/kilocode/`, `packages/kilo-*`) do not reference any of the removed/renamed types.

The 14 findings above are all places where a chain *could* be regressed by the merge but require a human to verify either:
- a runtime contract (string error matching, slash command names, telemetry event types),
- behavior of out-of-repo consumers (VSCode extension, JetBrains plugin, cloud, telemetry pipeline),
- or pre-existing assumptions in Kilo code that interacted with upstream behavior implicitly.

Top priorities for a human pass:
1. **Findings 1, 4, 8** — sync ownership / event ordering / removed `workspace.restore` event are most likely to surface as silent failures.
2. **Finding 7** — slash-command rename `/restore` → `/warp` user-visible change.
3. **Findings 11, 13** — telemetry/gateway consumers of `Step.Failed.error.type` and 3-segment model ids.
