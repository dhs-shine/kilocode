import type { KiloClient, SessionStatus } from "@kilocode/sdk/v2/client"
import { sameDirectory } from "../kilo-provider-utils"

export type ActiveSessionDirectories = Map<string, Map<string, SessionStatus>>

export function updateActiveSessionDirectory(input: {
  active: ActiveSessionDirectories
  sessionID: string
  status: SessionStatus
  dir?: string
}) {
  const target = input.dir
  if (!target) return
  const entries = input.active.get(input.sessionID)
  if (input.status.type === "idle") {
    if (!entries) return
    for (const dir of entries.keys()) {
      if (sameDirectory(dir, target)) entries.delete(dir)
    }
    if (entries.size === 0) input.active.delete(input.sessionID)
    return
  }
  if (!entries) {
    input.active.set(input.sessionID, new Map([[target, input.status]]))
    return
  }
  for (const dir of entries.keys()) {
    if (sameDirectory(dir, target)) entries.delete(dir)
  }
  entries.set(target, input.status)
}

export function resolveActiveSessionStatus(active: ActiveSessionDirectories, sessionID: string) {
  const statuses = [...(active.get(sessionID)?.values() ?? [])]
  return statuses[statuses.length - 1]
}

export function resolveAbortDirectories(active: ActiveSessionDirectories, sessionID: string, fallback: string) {
  const dirs = [...(active.get(sessionID)?.keys() ?? [])]
  if (!dirs.some((dir) => sameDirectory(dir, fallback))) dirs.push(fallback)
  return dirs
}

export async function abortSession(input: { client: KiloClient; sessionID: string; dir: string }) {
  await input.client.session.abort({ sessionID: input.sessionID, directory: input.dir }, { throwOnError: true })
}
