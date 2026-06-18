import { describe, expect, it } from "bun:test"
import type { KiloClient } from "@kilocode/sdk/v2/client"
import {
  abortSession,
  resolveAbortDirectories,
  updateActiveSessionDirectory,
  type ActiveSessionDirectories,
} from "../../src/kilo-provider/abort"

function client(calls: unknown[], fail = false) {
  return {
    session: {
      abort: async (params: unknown, opts: unknown) => {
        calls.push({ type: "abort", params, opts })
        if (fail) throw new Error("abort failed")
        return { data: true }
      },
    },
  } as unknown as KiloClient
}

describe("active session directories", () => {
  function update(active: ActiveSessionDirectories, status: "busy" | "idle", dir: string) {
    updateActiveSessionDirectory({ active, sessionID: "session_1", status, dir })
  }

  it("includes the active owner and current mapped directory", () => {
    const active: ActiveSessionDirectories = new Map()
    update(active, "busy", "/repo")

    expect(resolveAbortDirectories(active, "session_1", "/repo/worktree")).toEqual(["/repo", "/repo/worktree"])
  })

  it("removes an owner when its instance becomes idle", () => {
    const active: ActiveSessionDirectories = new Map()
    update(active, "busy", "/repo")
    update(active, "idle", "/repo")

    expect(resolveAbortDirectories(active, "session_1", "/repo/worktree")).toEqual(["/repo/worktree"])
  })

  it("deduplicates equivalent directory paths", () => {
    const active: ActiveSessionDirectories = new Map()
    update(active, "busy", "/repo/worktree")

    expect(resolveAbortDirectories(active, "session_1", "/repo/worktree/.")).toEqual(["/repo/worktree"])
  })
})

describe("abortSession", () => {
  it("calls session.abort with the session id and directory", async () => {
    const calls: unknown[] = []

    await abortSession({ client: client(calls), sessionID: "session_1", dir: "/repo" })

    expect(calls).toEqual([
      {
        type: "abort",
        params: { sessionID: "session_1", directory: "/repo" },
        opts: { throwOnError: true },
      },
    ])
  })

  it("rejects when the abort request fails", async () => {
    const calls: unknown[] = []

    await expect(abortSession({ client: client(calls, true), sessionID: "session_1", dir: "/repo" })).rejects.toThrow(
      "abort failed",
    )

    expect(calls).toEqual([
      {
        type: "abort",
        params: { sessionID: "session_1", directory: "/repo" },
        opts: { throwOnError: true },
      },
    ])
  })
})
