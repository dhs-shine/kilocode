import { describe, expect, it } from "bun:test"
import {
  AttentionTracker,
  delivery,
  sessionErrorMessage,
  type AttentionNotice,
  type AttentionSignal,
} from "../../src/services/attention/attention"
import { resolveSoundID } from "../../src/services/attention/sound"

function collect(signals: AttentionSignal[]) {
  const tracker = new AttentionTracker()
  const notices: AttentionNotice[] = []
  for (const signal of signals) {
    const notice = tracker.handle(signal)
    if (notice) notices.push(notice)
  }
  return notices
}

describe("AttentionTracker", () => {
  it("notifies once after an active session becomes idle", () => {
    const notices = collect([
      { type: "session", sessionID: "s1", title: "Fix the bug", parentID: null },
      { type: "status", sessionID: "s1", status: "active" },
      { type: "status", sessionID: "s1", status: "idle" },
      { type: "status", sessionID: "s1", status: "idle" },
    ])

    expect(notices).toEqual([
      {
        sessionID: "s1",
        kind: "done",
        title: "Fix the bug",
        message: "Session done",
        subagent: false,
      },
    ])
  })

  it("uses the subagent completion sound category", () => {
    const notices = collect([
      { type: "session", sessionID: "child", title: "Research", parentID: "parent" },
      { type: "status", sessionID: "child", status: "active" },
      { type: "status", sessionID: "child", status: "idle" },
    ])

    expect(notices[0]).toMatchObject({ kind: "subagent_done", subagent: true })
  })

  it("notifies for distinct questions and permissions while suppressing duplicate requests", () => {
    const notices = collect([
      { type: "question", sessionID: "s1", requestID: "q1", open: true },
      { type: "question", sessionID: "s1", requestID: "q1", open: true },
      { type: "question", sessionID: "s1", requestID: "q1", open: false },
      { type: "question", sessionID: "s1", requestID: "q1", open: true },
      { type: "permission", sessionID: "s1", requestID: "p1", open: true },
      { type: "permission", sessionID: "s1", requestID: "p1", open: true },
    ])

    expect(notices.map((notice) => notice.kind)).toEqual(["question", "question", "permission"])
  })

  it("notifies an active session error and suppresses its following completion", () => {
    const notices = collect([
      { type: "status", sessionID: "s1", status: "active" },
      { type: "error", sessionID: "s1", error: { name: "ApiError" } },
      { type: "status", sessionID: "s1", status: "idle" },
    ])

    expect(notices).toEqual([
      {
        sessionID: "s1",
        kind: "error",
        title: undefined,
        message: "Session error",
        subagent: false,
      },
    ])
  })

  it("clears active transitions when the connection resets", () => {
    const tracker = new AttentionTracker()
    tracker.handle({ type: "status", sessionID: "s1", status: "active" })
    tracker.reset()

    expect(tracker.handle({ type: "status", sessionID: "s1", status: "idle" })).toBeUndefined()
  })
})

describe("sessionErrorMessage", () => {
  it("classifies aborts and response timeouts", () => {
    expect(sessionErrorMessage({ name: "MessageAbortedError" })).toBe("Session aborted")
    expect(sessionErrorMessage({ data: { message: "SSE read timed out" } })).toBe("Model stopped responding")
    expect(sessionErrorMessage({ name: "ApiError" })).toBe("Session error")
  })
})

describe("delivery", () => {
  it("shows root notifications only while VS Code is blurred", () => {
    expect(
      delivery({
        appFocused: false,
        sessionFocused: true,
        subagent: false,
        notifications: true,
        sound: true,
        playWhenFocused: false,
      }),
    ).toEqual({ notification: true, sound: true })

    expect(
      delivery({
        appFocused: true,
        sessionFocused: true,
        subagent: false,
        notifications: true,
        sound: true,
        playWhenFocused: false,
      }),
    ).toEqual({ notification: false, sound: false })
  })

  it("plays sounds for background sessions and keeps subagent notifications silent", () => {
    expect(
      delivery({
        appFocused: true,
        sessionFocused: false,
        subagent: true,
        notifications: true,
        sound: true,
        playWhenFocused: false,
      }),
    ).toEqual({ notification: false, sound: true })
  })

  it("keeps visual and sound preferences independent", () => {
    expect(
      delivery({
        appFocused: false,
        sessionFocused: false,
        subagent: false,
        notifications: false,
        sound: true,
        playWhenFocused: false,
      }),
    ).toEqual({ notification: false, sound: true })
    expect(
      delivery({
        appFocused: false,
        sessionFocused: false,
        subagent: false,
        notifications: true,
        sound: false,
        playWhenFocused: true,
      }),
    ).toEqual({ notification: true, sound: false })
  })
})

describe("notification defaults", () => {
  it("keeps visual and sound notifications opt-in", async () => {
    const manifest = (await Bun.file(new URL("../../package.json", import.meta.url)).json()) as {
      contributes: { configuration: { properties: Record<string, { default?: unknown; enum?: unknown[] }> } }
    }
    const properties = manifest.contributes.configuration.properties

    expect(properties["kilo-code.new.notifications.agent"]?.default).toBe(false)
    expect(properties["kilo-code.new.notifications.permissions"]?.default).toBe(false)
    expect(properties["kilo-code.new.notifications.errors"]?.default).toBe(false)
    expect(properties["kilo-code.new.sounds.agentEnabled"]?.default).toBe(false)
    expect(properties["kilo-code.new.sounds.permissionsEnabled"]?.default).toBe(false)
    expect(properties["kilo-code.new.sounds.errorsEnabled"]?.default).toBe(false)
    expect(properties["kilo-code.new.sounds.agent"]?.enum).not.toContain("none")
  })
})

describe("resolveSoundID", () => {
  it("uses the OpenCode attention defaults", () => {
    expect(resolveSoundID("default", "done")).toBe("bip-bop-01")
    expect(resolveSoundID("default", "question")).toBe("bip-bop-03")
    expect(resolveSoundID("default", "permission")).toBe("staplebops-06")
    expect(resolveSoundID("default", "error")).toBe("nope-03")
    expect(resolveSoundID("default", "subagent_done")).toBe("yup-01")
  })

  it("accepts system and bundled sounds and rejects invalid values", () => {
    expect(resolveSoundID("system", "done")).toBe("system")
    expect(resolveSoundID("alert-04", "done")).toBe("alert-04")
    expect(resolveSoundID("none", "done")).toBeUndefined()
    expect(resolveSoundID("unknown", "done")).toBeUndefined()
  })
})
