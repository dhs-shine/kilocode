import { describe, expect, it } from "bun:test"
import { terminal } from "../../webview-ui/src/context/session-outcome"
import type { Message, Part, TodoItem } from "../../webview-ui/src/types/messages"

function message(finish?: string, error?: Message["error"]): Message {
  return {
    id: "m1",
    sessionID: "s1",
    role: "assistant",
    createdAt: new Date(0).toISOString(),
    finish,
    error,
  }
}

function todo(status: TodoItem["status"]): TodoItem {
  return { id: status, content: status, status }
}

function snapshotMessage(id = "snapshot"): Message {
  return {
    ...message("other"),
    id,
    cost: 0,
    tokens: { input: 0, output: 0, reasoning: 0, cache: { read: 0, write: 0 } },
  }
}

function snapshotParts(reason = "other"): Part[] {
  return [
    { id: "start", type: "step-start" },
    { id: "progress", type: "text", text: "Initializing snapshot...", synthetic: true },
    { id: "finish", type: "step-finish", reason },
  ]
}

function lookup(id: string, parts: Part[]): (msg: Message) => Part[] | undefined {
  return (msg) => (msg.id === id ? parts : msg.parts)
}

describe("terminal", () => {
  it("returns no terminal state before a turn closes", () => {
    expect(terminal({ messages: [message("stop")], todos: [] })).toBeUndefined()
  })

  it("hides normal completed turns", () => {
    expect(terminal({ reason: "completed", messages: [message("stop")], todos: [] })).toBeUndefined()
  })

  it("warns when a completed turn leaves to-dos unfinished", () => {
    expect(
      terminal({ reason: "completed", messages: [message("stop")], todos: [todo("completed"), todo("pending")] }),
    ).toEqual({ kind: "incomplete", tone: "warning", finish: "stop", remaining: 1 })
  })

  it("treats cancelled to-dos as terminal rather than remaining work", () => {
    expect(terminal({ reason: "completed", messages: [message("stop")], todos: [todo("cancelled")] })).toBeUndefined()
  })

  it("surfaces response limit and unknown model finishes before generic incomplete status", () => {
    expect(terminal({ reason: "completed", messages: [message("length")], todos: [todo("pending")] })?.kind).toBe(
      "limit",
    )
    expect(terminal({ reason: "completed", messages: [message("unknown")], todos: [] })?.kind).toBe("unknown")
  })

  it("surfaces filtered and unexpected provider finishes", () => {
    expect(terminal({ reason: "completed", messages: [message("content-filter")], todos: [] })?.kind).toBe("filtered")
    expect(terminal({ reason: "completed", messages: [message("other")], todos: [] })?.kind).toBe("unexpected")
  })

  it("ignores snapshot-only assistant tails when choosing the terminal finish", () => {
    const real = { ...message("length"), id: "real" }
    const snap = snapshotMessage()

    expect(
      terminal({
        reason: "completed",
        messages: [real, snap],
        todos: [],
        parts: lookup(snap.id, snapshotParts()),
      }),
    ).toEqual({ kind: "limit", tone: "warning", finish: "length", remaining: 0 })
  })

  it("uses inline parts to ignore snapshot-only assistant tails", () => {
    const real = { ...message("stop"), id: "real" }
    const snap = { ...snapshotMessage(), parts: snapshotParts() }

    expect(terminal({ reason: "completed", messages: [real, snap], todos: [todo("pending")] })).toEqual({
      kind: "incomplete",
      tone: "warning",
      finish: "stop",
      remaining: 1,
    })
  })

  it("keeps unexpected warnings for real other finishes", () => {
    const snap = {
      ...snapshotMessage(),
      parts: [...snapshotParts(), { id: "real", type: "text", text: "Actual assistant text" } satisfies Part],
    }

    expect(terminal({ reason: "completed", messages: [snap], todos: [] })?.kind).toBe("unexpected")
  })

  it("requires synthetic snapshot progress before ignoring an other finish", () => {
    const snap = {
      ...snapshotMessage(),
      parts: [{ id: "progress", type: "text", text: "Initializing snapshot..." } satisfies Part],
    }

    expect(terminal({ reason: "completed", messages: [snap], todos: [] })?.kind).toBe("unexpected")
  })

  it("surfaces interruption and failures without a rendered error", () => {
    expect(terminal({ reason: "interrupted", messages: [message("stop")], todos: [todo("pending")] })).toEqual({
      kind: "interrupted",
      tone: "warning",
      finish: "stop",
      remaining: 1,
    })
    expect(terminal({ reason: "error", messages: [message("error")], todos: [] })?.kind).toBe("error")
  })

  it("does not duplicate a concrete rendered failure", () => {
    expect(terminal({ reason: "error", messages: [message("error", { name: "APIError" })], todos: [] })).toBeUndefined()
  })

  it("retains a fallback failure when the concrete error is hidden", () => {
    expect(
      terminal({
        reason: "error",
        messages: [message("error", { name: "APIError" })],
        todos: [],
        hidden: () => true,
      })?.kind,
    ).toBe("error")
  })

  it("reports only the latest assistant finish reason", () => {
    const user: Message = { id: "u1", sessionID: "s1", role: "user", createdAt: new Date(1).toISOString() }
    expect(terminal({ reason: "completed", messages: [message("length"), user], todos: [] })?.finish).toBeUndefined()
  })
})
