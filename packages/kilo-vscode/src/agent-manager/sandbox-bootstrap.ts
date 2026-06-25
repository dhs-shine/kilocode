// Ensure a CLI session's sandbox override matches the desired state.
//
// `session.create` exposes no sandbox parameter, so sandbox state is reconciled
// after the session exists via `sandbox.toggle` (which flips state). This checks
// the current status first and toggles only when the state differs, so it is
// safe regardless of the global `experimental.sandbox` default.
//
// Pure runtime helper — no vscode imports; takes the SDK client directly so it
// can be unit-tested in isolation. Failures are logged and swallowed: sandbox is
// a best-effort safety enhancement, and a worktree stays usable if the sandbox
// backend is unavailable (the user can toggle it manually from the prompt).

import type { KiloClient } from "@kilocode/sdk/v2/client"

export async function ensureSandbox(
  client: KiloClient,
  sessionId: string,
  directory: string,
  desired: boolean,
  log: (msg: string) => void,
): Promise<void> {
  const sandbox = client.sandbox
  let current: boolean
  try {
    const { data } = await sandbox.status({ sessionID: sessionId, directory }, { throwOnError: true })
    if (!data.available) {
      log(`Sandbox unavailable for ${sessionId}: ${data.reason ?? "unknown"}`)
      return
    }
    current = data.enabled
  } catch (err) {
    log(`Sandbox status check failed for ${sessionId}: ${err instanceof Error ? err.message : String(err)}`)
    return
  }
  if (current === desired) return
  try {
    const { data } = await sandbox.toggle({ sessionID: sessionId, directory }, { throwOnError: true })
    if (!data.available) {
      log(`Sandbox toggle unavailable for ${sessionId}: ${data.reason ?? "unknown"}`)
      return
    }
    log(`Sandbox ${data.enabled ? "enabled" : "disabled"} for ${sessionId}`)
  } catch (err) {
    log(`Sandbox toggle failed for ${sessionId}: ${err instanceof Error ? err.message : String(err)}`)
  }
}
