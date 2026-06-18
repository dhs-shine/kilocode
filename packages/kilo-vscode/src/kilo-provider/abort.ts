import type { KiloClient, SessionStatus } from "@kilocode/sdk/v2/client"
import { sameDirectory } from "../kilo-provider-utils"

export type ActiveSessionDirectories = Map<string, Set<string>>

export function updateActiveSessionDirectory(input: {
  active: ActiveSessionDirectories
  sessionID: string
  status: SessionStatus["type"]
  dir?: string
}) {
  const target = input.dir
  if (!target) return
  const dirs = input.active.get(input.sessionID)
  if (input.status === "idle") {
    if (!dirs) return
    for (const dir of dirs) {
      if (sameDirectory(dir, target)) dirs.delete(dir)
    }
    if (dirs.size === 0) input.active.delete(input.sessionID)
    return
  }
  if (!dirs) {
    input.active.set(input.sessionID, new Set([target]))
    return
  }
  if (![...dirs].some((dir) => sameDirectory(dir, target))) dirs.add(target)
}

export function resolveAbortDirectories(active: ActiveSessionDirectories, sessionID: string, fallback: string) {
  const dirs = [...(active.get(sessionID) ?? [])]
  if (!dirs.some((dir) => sameDirectory(dir, fallback))) dirs.push(fallback)
  return dirs
}

export async function abortSession(input: { client: KiloClient; sessionID: string; dir: string }) {
  await input.client.session.abort({ sessionID: input.sessionID, directory: input.dir }, { throwOnError: true })
}
