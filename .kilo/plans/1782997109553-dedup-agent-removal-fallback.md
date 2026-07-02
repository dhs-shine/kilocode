# Remove duplicate client-side agent-removal in VS Code extension

## Goal

Make the VS Code Settings → **Remove agent** action rely solely on the CLI
`/kilocode/agent/remove` endpoint, deleting the redundant client-side marketplace
fallback that re-implements `kilo.json`/`.md` removal.

The CLI endpoint (`KiloAgent.remove` in
`packages/opencode/src/kilocode/agent/index.ts`) already removes, across **all**
scopes in one call:

- `agent/**` and `mode/**` `.md` files under every `config.directories()` dir
- inline `kilo.json`/`kilo.jsonc` `agent[name]` entries **and** `default_agent`
  cleanup (`removeConfigAgent`, via `KilocodeConfigOverlay` global/project targets)
- legacy `.kilocodemodes` YAML entries

So for the settings path the client fallback is redundant.

## Scope (decided)

- **In scope:** Settings "Remove agent" path only.
- **Out of scope:** Marketplace panel agent uninstall (`removeMarketplaceItem`),
  `MarketplaceInstaller.removeAgent`, MCP removal, and any CLI change.
- **No CLI change is required** for this scope. `KiloAgent.remove` already has
  full parity with the client fallback. (See Risks for the one edge case.)

All edits are confined to `packages/kilo-vscode/` — no `kilocode_change` markers
needed there.

## Tasks (ordered)

1. **`src/KiloProvider.ts` — `handleRemoveAgent` (around line 2284).**
   Remove the marketplace fallback. Rely only on
   `this.client.kilocode.removeAgent({ name, directory })`. Model the error
   handling on the existing `removeSkillViaCli` (around line 2257): on
   `result.error` or a thrown exception, log it, invalidate the agents cache
   (`this.cachedAgentsMessage = null`), re-fetch (`await this.fetchAndSendAgents()`),
   and `this.requirements.clear()` so the webview reverts to authoritative state.
   Drop the `try/catch { /* fall through */ }` + `removeAgent(this.removeConfigItemCtx, name)`
   block entirely.

2. **`src/KiloProvider.ts` — import (line 56).**
   Change `import { createMarketplaceRemover, removeAgent, removeMcp } from "./kilo-provider/remove-config-item"`
   to drop `removeAgent`. Keep `createMarketplaceRemover` and `removeMcp`
   (still used by `handleRemoveMcp` and `marketplaceRemove`).

3. **`src/kilo-provider/remove-config-item.ts`.**
   Delete the now-unused `removeAgent` function (lines 22-24). Keep `removeMcp`,
   `RemoveConfigItemContext`, `createMarketplaceRemover`, and the private `remove`
   helper (still used by `removeMcp`). Required: `knip` fails on unused exports.

4. **`tests/unit/remove-config-item.test.ts`.**
   Remove the two `removeAgent` test cases (the "removes agents from project and
   global scopes" case and the "does not refresh when removal fails" case, which
   uses `removeAgent`). Keep the MCP case. Update the import on line 2 to drop
   `removeAgent`. If the "does not refresh on failure" behavior should still be
   covered, re-express it using `removeMcp` instead.

5. **`tests/unit/marketplace-panel-arch.test.ts` (line 29).**
   Remove the `expect(kilo).toContain("removeAgent(this.removeConfigItemCtx, name)")`
   assertion. Keep the `removeMcp(this.removeConfigItemCtx, name)` assertion and the
   rest of the `remove-config-item.ts` structural assertions.

## Risks / behavior change

- **Orphaned agent entries.** The endpoint first resolves the agent via
  `agents.get(name)`; if a `kilo.json` entry failed to load into the resolved
  agent map (e.g. invalid schema), `agents.get` returns `undefined` and
  `KiloAgent.remove` throws `RemoveError` → `BadRequest`. The old fallback would
  still have force-deleted the file/config. Low likelihood, because the Settings
  UI only lists resolved agents. If this becomes a real problem, the follow-up is
  a CLI hardening (allow `remove` to proceed on config/file matches even when
  `agents.get` is `undefined`) — intentionally deferred.
- Removing the fallback means genuine CLI failures now surface as a failed
  removal (cache reverts) instead of being silently patched over client-side.
  This is the intended, more honest behavior.

## Validation

From `packages/kilo-vscode/`:

- `bun run typecheck`
- `bun run lint`
- `bun run knip` (confirms no unused `removeAgent` export remains)
- `bun run test:unit` (the two edited test files must pass)

Manual smoke test (extension host):

- Remove a `.md`-defined custom agent from Settings → agent disappears, files gone.
- Remove a `kilo.json`-defined agent from Settings → entry removed, `default_agent`
  cleared if it pointed at it.
- Simulate a backend error and confirm the agents list reverts to authoritative
  state instead of showing a phantom removal.

## Out-of-scope follow-ups (not part of this plan)

- Route the Marketplace panel agent uninstall through the CLI (needs a `scope`
  param on the endpoint) and delete `MarketplaceInstaller.removeAgent`'s
  `kilo.json`/`.md` hand-editing.
- Add a CLI `/kilocode/mcp/remove` endpoint and remove `MarketplaceInstaller.removeMcp`
  duplication.
- CLI hardening for orphaned/unloaded agent entries.
