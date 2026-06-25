# Plan: Persist Sandbox State Per Session

## Goal

Make the sandbox toolbar control behave predictably across Agent Manager, the sidebar, and editor tabs:

- Every existing session remembers its own last sandbox state across tab switches and restarts.
- The most recently selected sandbox state becomes the default for brand-new sessions.
- Changing the default never changes another existing session.
- A fork inherits the source session's sandbox state because it is a continuation, not a fresh session.
- Backend enforcement remains authoritative. The UI must never present sandboxing as active when the backend cannot enforce it.

Auto Approve scope is intentionally excluded and tracked separately in https://github.com/Kilo-Org/kilocode/issues/11673.

## Expected State Model

Use two durable values with different responsibilities:

1. **Session state**: Store the desired sandbox state in the existing session metadata under a Kilo-owned key such as `kilocode.sandbox`.
2. **New-session default**: Store the most recently selected state in a shared VS Code `globalState` preference. Fall back to `experimental.sandbox` until the user explicitly selects a state.

The effective backend state is:

```text
(session metadata value ?? configured default) && sandbox backend is available
```

A successful toggle in an existing session updates both that session and the new-session default. It does not update other existing sessions.

Example:

```text
Configured default: enabled
Create A                         -> A enabled
Disable sandbox in A             -> A disabled, new-session default disabled
Create B                         -> B disabled
Enable sandbox in B              -> B enabled, new-session default enabled
Switch back to A                 -> A remains disabled
Create C                         -> C enabled
Fork A                           -> fork disabled
Fork B                           -> fork enabled
```

## Behavior Matrix

| Flow | Required behavior |
|---|---|
| Blank new prompt | Show the sticky new-session default. Toggling changes only the default and must not create an empty backend session. |
| First prompt send | Create the session with the currently displayed default already stored in metadata before any tool can execute. |
| Explicit new local session | Initialize metadata from the sticky default. |
| New Agent Manager worktree session | Initialize metadata from the sticky default in the worktree directory. |
| Existing session load | Read the persisted state from the backend. Use `experimental.sandbox` only for legacy sessions without metadata. |
| Session switch | Fetch the selected session's state and discard stale responses from the previously selected session. |
| Existing session toggle | Persist the selected state in that session, then update the sticky default. Do not alter any other session. |
| Session fork | Copy the source session metadata. Ignore the sticky default. Parent and child become independent after the fork. |
| Continue in Worktree | Preserve the source session state through the existing fork flow. |
| Move or promote a session | Preserve state because the same session is being moved or associated, not created. |
| Session deletion | Delete state with the session row and retain serialization against an in-flight toggle. |
| Backend restart | Reload existing session state from metadata rather than reverting to config. |
| VS Code reload | Reload the sticky default from `globalState` and existing state from backend metadata. |
| Sidebar and editor tabs | Use the same shared default service; session state remains backend-owned. |
| Cloud preview | Keep the control hidden for synthetic `cloud:` sessions. |
| Cloud continuation/import | Preserve imported session metadata. Do not overwrite it with the local new-session default. |
| Unsupported platform | Show the backend reason, disable the control, and never display sandbox as effectively enabled. Preserve desired metadata for portability. |
| Config change | Affect only legacy sessions without explicit metadata and future defaults when no sticky preference exists. Never overwrite explicit session state. |

## Implementation

### 1. Persist Session State in Kilo-Owned Metadata

Add a Kilo-owned metadata helper under `packages/opencode/src/kilocode/sandbox/` that:

- Validates and reads `{ enabled: boolean, version: number }` from `Session.Info.metadata["kilocode.sandbox"]`.
- Merges updates without replacing unrelated session metadata.
- Treats missing or malformed metadata as absent and falls back safely to `experimental.sandbox`.
- Stores desired state separately from effective availability.

Update `packages/opencode/src/kilocode/sandbox/policy.ts`:

- Replace the process-local `overrides` value map with metadata reads and writes.
- Keep per-session locking so concurrent toggles and deletion remain serialized.
- Key durable state by session ID, not by `(directory, session ID)`.
- Persist `version` so stale HTTP or SSE responses can still be rejected after a backend restart.
- Continue publishing the existing session-scoped sandbox change event.
- Continue checking effective state immediately before every tool and MCP execution.

Use the existing session metadata column and APIs. Do not add a database migration or a new upstream session field. Keep Kilo behavior in `packages/opencode/src/kilocode/`; avoid new shared upstream changes.

Update the sandbox HTTP handler to persist through the session service so normal session update and synchronization behavior is retained. Update the route description to remove the word `ephemeral`.

### 2. Expose Sessionless Backend Support

Add a Kilo-owned `GET /sandbox/support` endpoint returning:

```ts
{
  available: boolean
  reason?: string
}
```

A blank prompt has no session ID, so it cannot reliably infer support from a session status endpoint or `process.platform`. Linux support also depends on the Bubblewrap probe.

Regenerate OpenAPI and `packages/sdk/js/` after adding the endpoint.

### 3. Add a Shared Sticky Default Service

Add a small extension service, for example `packages/kilo-vscode/src/services/sandbox-preference.ts`, backed by `ExtensionContext.globalState`.

The service should:

- Store a tri-state value: absent, enabled, or disabled.
- Resolve absent state from `experimental.sandbox`.
- Broadcast changes to the sidebar, editor tabs, and Agent Manager providers.
- Serialize writes and expose an awaitable pending update for first-prompt ordering.
- Remain machine-local and not opt into Settings Sync because backend support is machine-specific.

Rules for updating it:

- Blank-prompt toggle: update the default only.
- Existing-session toggle: update it only after the backend successfully persists the session state.
- Session load, switch, fork, import, or move: do not update it merely because a session became active.
- Failed or unavailable backend toggle: do not claim a new remembered state.

### 4. Separate Blank Defaults From Session Status in the Webview Protocol

Add explicit messages for requesting and updating the blank-prompt default instead of overloading a missing session ID:

```ts
// webview -> extension
{ type: "requestSandboxDefault" }
{ type: "setSandboxDefault", enabled, requestID, draftID? }

// extension -> webview
{
  type: "sandboxDefaultStatus"
  desired: boolean
  enabled: boolean
  available: boolean
  reason?: string
  revision: number
  requestID?: string
}
```

Update `PromptInput.tsx` so that:

- A real session renders only matching backend session status.
- A blank or pending prompt renders only the shared default status.
- Switching sessions clears stale state before requesting the selected scope.
- Reconnect requests current support plus either the blank default or active session status.
- The button is disabled while a default or session update is pending.
- Unsupported state shows the backend reason.
- Cloud previews continue hiding the control.

### 5. Snapshot the Default During Every Fresh Session Creation

Create one extension helper that merges the resolved default into the session create payload:

```ts
metadata: {
  ...metadata,
  "kilocode.sandbox": {
    enabled: defaultValue,
    version: 0,
  },
}
```

Use it for all fresh-session paths:

- Sidebar and editor-tab first prompt.
- Explicit local session creation.
- Agent Manager pending local tab on first prompt.
- New worktree creation.
- Adding a new session to an existing worktree.
- Agent Manager tool-created local and worktree sessions.
- Imported existing branches/worktrees when they create a genuinely new session.

Do not use the helper for:

- `session.fork`.
- Continue in Worktree.
- Moving or promoting an existing session.
- Cloud import/continuation.

Session creation must wait for any in-flight blank default update. The metadata must be present in the create request so the first prompt cannot execute tools under the wrong state.

### 6. Preserve Fork Semantics

The backend session fork already clones metadata. Add sandbox-specific tests to lock in these semantics:

- A disabled parent creates a disabled fork even when the sticky default is enabled.
- An enabled parent creates an enabled fork even when the sticky default is disabled.
- Toggling the child does not change the parent.
- Toggling the parent does not change the child.
- Continue in Worktree preserves the source value across directory changes.
- Forking or selecting a fork does not itself change the sticky default.

Legacy sessions without sandbox metadata continue using the configured fallback. Once a user toggles one, it receives explicit durable metadata.

### 7. Retain Safety and Race Guarantees

Preserve or strengthen the current guards:

- Serialize toggle against session deletion.
- Reject stale status by session ID, directory, backend version, and provider revision.
- Do not let a first prompt overtake a pending default update.
- Do not mutate the sticky default if backend persistence fails.
- Do not report effective enabled state when the sandbox backend is unavailable.
- Keep sandbox enforcement independent of Auto Approve and permission responses.

## Tests

### Backend

Update `packages/opencode/test/kilocode/sandbox/state.test.ts` and related Kilo-owned tests to cover:

- Enabled and disabled values survive backend/database restart.
- Explicit session state overrides config in both directions.
- Legacy and malformed metadata safely fall back to config.
- Unrelated metadata is preserved.
- Version persists and increments.
- Two sessions remain isolated.
- The same session reports the same desired state across directory routing.
- Unsupported backend reports effective disabled without destroying desired state.
- Concurrent toggles remain serialized.
- Toggle versus deletion cannot recreate deleted state.
- Forks inherit state and become independent.

Add API coverage for the support endpoint and persisted status/toggle responses.

### VS Code Extension

Add or update focused unit tests for:

- Missing sticky preference falls back to config.
- Explicit false overrides config true, and explicit true overrides config false.
- Preference survives construction of a new provider/service.
- Blank toggle does not create a session.
- First send waits for the pending default update and creates exactly one session with matching metadata.
- Existing-session toggle updates that session plus the sticky default, but no other session.
- Switching A to B to A restores each session's backend state.
- Worktree and Agent Manager tool creation include default metadata.
- Fork and Continue in Worktree do not apply the new-session helper.
- Move, promote, and cloud import do not overwrite existing metadata.
- Multiple providers receive sticky default broadcasts while session events remain filtered by tracked session ID.
- Unsupported support response disables the blank control without creating a session or changing the preference.
- Reconnect ignores stale pre-reconnect responses.

### Manual Test

1. Start with configured sandbox enabled. Create A, disable it, open a new local tab, and confirm the blank toggle is disabled.
2. Send the first prompt in the new tab and confirm B remains disabled during its first tool execution.
3. Enable sandbox in B, switch back to A, and confirm A remains disabled. Switch to B and confirm enabled.
4. Create C and confirm it starts enabled from the latest selected default.
5. Fork A and B. Confirm each fork inherits its parent, then toggle a fork and verify its parent is unchanged.
6. Repeat creation and switching with an Agent Manager worktree session and Continue in Worktree.
7. Reload VS Code and restart the CLI backend. Confirm A, B, C, and the sticky blank default retain their states.
8. On an unsupported platform or forced unavailable backend, confirm the control is disabled with a reason and tools run without a false sandbox-enabled indication.

## Verification

Run the smallest relevant checks first, then the package guards affected by generated API and shared integration points:

```bash
# From the repository root after changing server endpoints
./script/generate.ts
bun run script/check-opencode-annotations.ts
bun run script/check-opencode-promise-facades.ts

# From packages/opencode
bun run typecheck
bun test test/kilocode/sandbox/state.test.ts

# From packages/kilo-vscode
bun run typecheck
bun run lint
bun run test:unit
bun run knip
bun run check-kilocode-change
```

Run `bun run script/extract-source-links.ts` only if implementation changes or adds URLs in the guarded packages. Add a patch changeset describing that sandbox choices now persist per session and initialize new sessions from the last selected state.

## Non-Goals

- Changing granular permission rules.
- Changing Auto Approve scope or persistence.
- Applying a sandbox toggle retroactively to every open session.
- Storing sandbox state in `.kilo/agent-manager.json`.
- Adding a new database table or modifying shared upstream session schemas.
