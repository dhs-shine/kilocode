# De-duplicate custom-agent removal onto the CLI endpoint

## Goal

One source of truth for custom-agent removal: the CLI `remove` function behind
`POST /kilocode/agent/remove`. VS Code Settings and JetBrains both delete agents
through that endpoint; neither re-implements removal on the settings path.
Unrelated CLI churn on this branch is reverted.

## Scope (decided)

- **In scope:** the agent-behavior **Settings "Remove agent"** path shared by VS
  Code and JetBrains, plus the CLI code that backs it.
- **Out of scope:** the VS Code **Marketplace panel** agent uninstall and
  `MarketplaceInstaller.removeAgent` (separate install/uninstall lifecycle,
  scope-specific, also handles skills/MCP). Do not touch it — that would be
  general VS Code cleanup outside this PR.
- **No general VS Code cleanup.** Only touch agent-removal code.
- **No CLI endpoint scope parameter** (only needed for the marketplace path,
  which is out of scope).

## Current state (most of this is already committed on the branch)

- CLI is already the single implementation: `remove` deletes `.md` files across
  `config.directories()`, removes inline `kilo.json`/`kilo.jsonc` `agent[name]`
  entries and clears `default_agent` (`removeConfigAgent`), and removes legacy
  `.kilocodemodes` entries.
- VS Code Settings `handleRemoveAgent` already calls the endpoint only; the
  client-side `removeAgent` helper is already deleted from `remove-config-item.ts`.
- JetBrains already POSTs to `/kilocode/agent/remove`.

So this plan mainly **locks in / verifies** that state, applies one small tidy,
and **reverts one unrelated churn file**.

## Tasks (ordered)

1. **CLI — verify and keep (no new work).**
   - `packages/opencode/src/kilocode/agent/index.ts`: `removeConfigAgent` and its
     call inside `remove` (scans overlay global + project targets, clears
     `default_agent`). This is the common code.
   - `packages/opencode/src/kilocode/server/httpapi/handlers/kilocode.ts`: the
     handler maps `KiloAgent.RemoveError → HttpApiError.BadRequest` (400) so
     clients get a clean failure instead of a 500. Keep.
   - `packages/opencode/test/kilocode/agent-remove.test.ts`: config-backed removal
     test. Keep.
   - `packages/opencode/test/kilocode/server/httpapi-exercise-scenarios.ts`: the
     `/kilocode/agent/remove` missing→400 scenario. Keep.

2. **VS Code — keep, with a minor tidy (agent-removal code only).**
   - `src/KiloProvider.ts` `handleRemoveAgent`: keep relying solely on
     `this.client.kilocode.removeAgent(...)`. Collapse the three repeated
     `cachedAgentsMessage = null` / `fetchAndSendAgents()` / `requirements.clear()`
     blocks into a single refresh after the `try/catch`, so the authoritative
     agents list is re-fetched on both success and failure. Log endpoint errors
     and exceptions; do not re-add any client-side `kilo.json`/`.md` editing.
   - `src/kilo-provider/remove-config-item.ts`: `removeAgent` stays removed; keep
     `removeMcp`, `RemoveConfigItemContext`, `createMarketplaceRemover`.
   - Tests `tests/unit/remove-config-item.test.ts` and
     `tests/unit/marketplace-panel-arch.test.ts`: keep the updates that dropped the
     agent-removal adapter assertions.
   - Do **not** modify `src/services/marketplace/installer.ts` or the Marketplace
     panel uninstall.

3. **JetBrains — verify only, no code change.**
   - Confirm `KiloAgentBehaviorRpcApiImpl.removeAgent` (backend) POSTs to
     `/kilocode/agent/remove` and the frontend surfaces the friendly delete
     failure on a 400. No change needed.

4. **Revert out-of-scope CLI churn.**
   - Restore `packages/opencode/test/kilocode/recall-search.test.ts` to
     `origin/main` (undo the `sessions` → `service` variable rename; unrelated to
     agent removal). Suggested: `git checkout origin/main -- packages/opencode/test/kilocode/recall-search.test.ts`.
   - **Keep** the `auth.set` (`PUT /auth/{providerID}`) and `mcp.add` (`POST /mcp`)
     exercise scenarios — plausible coverage for the branch's MCP/auth settings.

## Risks / behavior notes

- **Orphaned / unresolved agent entries.** The endpoint resolves the agent via
  `agents.get(name)` first; if a `kilo.json` entry failed to load into the
  resolved agent map, `agents.get` is `undefined` and `remove` throws
  `RemoveError` → 400, so it can't be removed from the UI. Low likelihood (the
  settings UI lists only resolved agents); matches JetBrains behavior; accepted.
- Dropping VS Code's client-side fallback means genuine CLI failures now surface
  as a failed removal (list reverts) instead of being silently patched over
  client-side. Intended.

## Validation

opencode (`packages/opencode/`):
- `bun test test/kilocode/agent-remove.test.ts`
- run the httpapi exercise scenario suite that consumes
  `httpapi-exercise-scenarios.ts`
- `bun run typecheck`
- confirm `git diff origin/main...HEAD -- packages/opencode/test/kilocode/recall-search.test.ts` is empty after the revert

kilo-vscode (`packages/kilo-vscode/`):
- `bun run typecheck`
- `bun run lint`
- `bun run knip` (no unused exports after the tidy)
- `bun test tests/unit/remove-config-item.test.ts tests/unit/marketplace-panel-arch.test.ts`

Manual smoke:
- VS Code Settings and JetBrains: remove a `.md`-defined agent and a
  `kilo.json`-defined agent; confirm both disappear, files/entries are gone, and
  `default_agent` is cleared when it pointed at the removed agent.
- Confirm a global-scope (`~/.config/kilo`) agent is removable via the endpoint.

## Out-of-scope follow-ups (not in this PR)

- Route the VS Code Marketplace panel agent uninstall through the CLI (would need
  a `scope` param on the endpoint) and delete `MarketplaceInstaller.removeAgent`'s
  `.md`/`kilo.json` editing.
- CLI hardening to remove orphaned/unresolved `kilo.json` agents even when
  `agents.get` is `undefined`.
