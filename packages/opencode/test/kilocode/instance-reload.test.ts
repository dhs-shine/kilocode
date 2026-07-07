import { describe, expect, it } from "bun:test"
import { hasBusySession } from "@/kilocode/server/httpapi/handlers/instance-reload"
import type { SessionStatus } from "@/session/status"
import { SessionID } from "@/session/schema"

const entries = (items: [string, SessionStatus.Info][]) =>
  items.map(([k, v]) => [k as SessionID, v] as [SessionID, SessionStatus.Info])

describe("instance-reload hasBusySession", () => {
  it("returns false for an empty map", () => {
    expect(hasBusySession(new Map(entries([])))).toBe(false)
  })

  it("returns false when all sessions are idle", () => {
    expect(
      hasBusySession(
        new Map(
          entries([
            ["s1", { type: "idle" }],
            ["s2", { type: "idle" }],
          ]),
        ),
      ),
    ).toBe(false)
  })

  it("returns true when any session is busy", () => {
    expect(
      hasBusySession(
        new Map(
          entries([
            ["s1", { type: "idle" }],
            ["s2", { type: "busy" }],
          ]),
        ),
      ),
    ).toBe(true)
  })

  it("returns true when the only session is busy", () => {
    expect(hasBusySession(new Map(entries([["s1", { type: "busy" }]])))).toBe(true)
  })
})
