import type { KiloClient, SessionStatus } from "@kilocode/sdk/v2/client"

/**
 * Fetch all current session statuses and seed the provided map + webview.
 * Called on connect so the Settings panel knows about already-running sessions
 * without waiting for the next session.status SSE event.
 *
 * When `reconcile` is true (default: first seed), locally-busy sessions absent
 * from the server response are reset to idle — covering server crash/restart.
 * On SSE reconnects set `reconcile: false` to avoid a race where the HTTP
 * fetch briefly returns stale data and the spinner disappears mid-stream.
 */
export async function seedSessionStatuses(
  client: KiloClient,
  dir: string,
  map: Map<string, SessionStatus["type"]>,
  post: (msg: unknown) => void,
  reconcile = true,
  update?: (sessionID: string, status: SessionStatus, source: "snapshot" | "reconcile") => SessionStatus | undefined,
): Promise<void> {
  try {
    const result = await client.session.status({ directory: dir })
    if (!result.data) return
    const active = result.data

    // Seed/update entries the server knows about
    for (const [sid, info] of Object.entries(active) as [string, SessionStatus][]) {
      const status = update ? update(sid, info, "snapshot") : info
      if (!status) continue
      map.set(sid, status.type)
      post({
        type: "sessionStatus",
        sessionID: sid,
        status: status.type,
        ...(status.type === "retry" ? { attempt: status.attempt, message: status.message, next: status.next } : {}),
      })
    }

    // Reconcile: any locally non-idle session absent from the server response
    // means the server lost its in-memory state (crash/restart). Reset to idle.
    // Skipped on SSE reconnects — the real-time SSE events are authoritative
    // for status transitions and the brief HTTP fetch can race with them.
    if (reconcile) {
      for (const [sid, status] of map) {
        if (status !== "idle" && !active[sid]) {
          const next = update ? update(sid, { type: "idle" }, "reconcile") : { type: "idle" as const }
          if (!next) continue
          map.set(sid, next.type)
          post({
            type: "sessionStatus",
            sessionID: sid,
            status: next.type,
            ...(next.type === "retry" ? { attempt: next.attempt, message: next.message, next: next.next } : {}),
          })
        }
      }
    }
  } catch (error) {
    console.error("[Kilo New] KiloProvider: Failed to seed session statuses:", error)
  }
}
