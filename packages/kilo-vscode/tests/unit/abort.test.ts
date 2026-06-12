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
  function update(active: ActiveSessionDirectories, status: "busy" | "retry" | "idle", dir?: string) {
    updateActiveSessionDirectory({ active, sessionID: "session_1", status, dir })
  }

  it("includes the active owner and current session directory", () => {
    const active: ActiveSessionDirectories = new Map()
    update(active, "busy", "/repo/source")

    expect(resolveAbortDirectories(active, "session_1", "/repo/worktree")).toEqual(["/repo/source", "/repo/worktree"])
  })

  it("falls back to the current session directory after the active turn becomes idle", () => {
    const active: ActiveSessionDirectories = new Map()
    update(active, "busy", "/repo/source")
    update(active, "idle", "/repo/source")

    expect(resolveAbortDirectories(active, "session_1", "/repo/worktree")).toEqual(["/repo/worktree"])
  })

  it("retains active directories when an unrelated instance reports idle", () => {
    const active: ActiveSessionDirectories = new Map()
    update(active, "retry", "/repo/source")
    update(active, "idle", "/repo/worktree")
    update(active, "idle")

    expect(resolveAbortDirectories(active, "session_1", "/repo/worktree")).toEqual(["/repo/source", "/repo/worktree"])
  })

  it("tracks concurrent instances and ignores delayed idle events from the old one", () => {
    const active: ActiveSessionDirectories = new Map()
    update(active, "busy", "/repo/source")
    update(active, "busy", "/repo/worktree")

    expect(resolveAbortDirectories(active, "session_1", "/repo/fallback")).toEqual([
      "/repo/source",
      "/repo/worktree",
      "/repo/fallback",
    ])

    update(active, "idle", "/repo/source")
    expect(resolveAbortDirectories(active, "session_1", "/repo/fallback")).toEqual(["/repo/worktree", "/repo/fallback"])
  })

  it("deduplicates the current directory when it is already active", () => {
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
