import type { SSEPayload } from "../cli-backend/sdk-sse-adapter"

export type AttentionKind = "done" | "subagent_done" | "question" | "permission" | "error"

export type AttentionSignal =
  | {
      type: "session"
      sessionID: string
      title?: string | null
      parentID?: string | null
      deleted?: boolean
    }
  | { type: "status"; sessionID: string; status: "active" | "idle" }
  | { type: "question"; sessionID: string; requestID: string; open: boolean }
  | { type: "permission"; sessionID: string; requestID: string; open: boolean }
  | { type: "error"; sessionID: string; error?: unknown }

export type AttentionNotice = {
  sessionID: string
  kind: AttentionKind
  title?: string
  message: string
  subagent: boolean
}

type Meta = {
  title?: string
  parentID?: string
}

type SessionSignal = Extract<AttentionSignal, { type: "session" }>
type StatusSignal = Extract<AttentionSignal, { type: "status" }>
type RequestSignal = Extract<AttentionSignal, { type: "question" | "permission" }>
type ErrorSignal = Extract<AttentionSignal, { type: "error" }>

type DeliveryInput = {
  appFocused: boolean
  sessionFocused: boolean
  subagent: boolean
  notifications: boolean
  sound: boolean
  playWhenFocused: boolean
}

export function delivery(input: DeliveryInput) {
  return {
    notification: input.notifications && !input.appFocused && !input.subagent,
    sound: input.sound && (input.playWhenFocused || !input.appFocused || !input.sessionFocused),
  }
}

export function sessionErrorMessage(error: unknown) {
  if (!error || typeof error !== "object") return "Session error"
  if ("name" in error && error.name === "MessageAbortedError") return "Session aborted"
  if (!("data" in error) || !error.data || typeof error.data !== "object") return "Session error"
  if (!("message" in error.data) || error.data.message !== "SSE read timed out") return "Session error"
  return "Model stopped responding"
}

export class AttentionTracker {
  private readonly active = new Set<string>()
  private readonly errored = new Set<string>()
  private readonly questions = new Set<string>()
  private readonly permissions = new Set<string>()
  private readonly sessions = new Map<string, Meta>()

  handle(signal: AttentionSignal): AttentionNotice | undefined {
    switch (signal.type) {
      case "session":
        return this.handleSession(signal)
      case "question":
        return this.handleRequest(signal, this.questions, "question", "Question needs input")
      case "permission":
        return this.handleRequest(signal, this.permissions, "permission", "Permission needs input")
      case "error":
        return this.handleError(signal)
      case "status":
        return this.handleStatus(signal)
    }
  }

  reset() {
    this.active.clear()
    this.errored.clear()
    this.questions.clear()
    this.permissions.clear()
  }

  dispose() {
    this.reset()
    this.sessions.clear()
  }

  private handleSession(signal: SessionSignal): AttentionNotice | undefined {
    if (signal.deleted) {
      this.sessions.delete(signal.sessionID)
      this.active.delete(signal.sessionID)
      this.errored.delete(signal.sessionID)
      return
    }
    const current = this.sessions.get(signal.sessionID) ?? {}
    const title = signal.title === undefined ? current.title : signal.title || undefined
    const parentID = signal.parentID === undefined ? current.parentID : signal.parentID || undefined
    this.sessions.set(signal.sessionID, { title, parentID })
  }

  private handleRequest(signal: RequestSignal, requests: Set<string>, kind: AttentionKind, message: string) {
    if (!signal.open) {
      requests.delete(signal.requestID)
      return
    }
    if (requests.has(signal.requestID)) return
    requests.add(signal.requestID)
    return this.notice(signal.sessionID, kind, message)
  }

  private handleError(signal: ErrorSignal) {
    if (!this.active.has(signal.sessionID)) return
    this.errored.add(signal.sessionID)
    return this.notice(signal.sessionID, "error", sessionErrorMessage(signal.error))
  }

  private handleStatus(signal: StatusSignal) {
    if (signal.status === "active") {
      this.active.add(signal.sessionID)
      this.errored.delete(signal.sessionID)
      return
    }
    if (!this.active.has(signal.sessionID)) return
    this.active.delete(signal.sessionID)
    if (this.errored.delete(signal.sessionID)) return
    const session = this.sessions.get(signal.sessionID)
    return this.notice(signal.sessionID, session?.parentID ? "subagent_done" : "done", "Session done")
  }

  private notice(sessionID: string, kind: AttentionKind, message: string): AttentionNotice {
    const session = this.sessions.get(sessionID)
    return {
      sessionID,
      kind,
      title: session?.title,
      message,
      subagent: session?.parentID !== undefined,
    }
  }
}

export function toAttentionSignal(event: SSEPayload): AttentionSignal | undefined {
  if (event.type === "sync") {
    if (event.name === "session.created.1") {
      return {
        type: "session",
        sessionID: event.data.sessionID,
        title: event.data.info.title,
        parentID: event.data.info.parentID ?? null,
      }
    }
    if (event.name === "session.updated.1") {
      return {
        type: "session",
        sessionID: event.data.sessionID,
        title: event.data.info.title,
        parentID: event.data.info.parentID,
      }
    }
    if (event.name === "session.deleted.1") {
      return { type: "session", sessionID: event.data.sessionID, deleted: true }
    }
    return
  }

  if (event.type === "session.status") {
    const status = event.properties.status.type
    if (status === "busy" || status === "retry") {
      return { type: "status", sessionID: event.properties.sessionID, status: "active" }
    }
    if (status === "idle") return { type: "status", sessionID: event.properties.sessionID, status: "idle" }
    return
  }

  if (event.type === "question.asked") {
    return {
      type: "question",
      sessionID: event.properties.sessionID,
      requestID: event.properties.id,
      open: true,
    }
  }
  if (event.type === "question.replied" || event.type === "question.rejected") {
    return {
      type: "question",
      sessionID: event.properties.sessionID,
      requestID: event.properties.requestID,
      open: false,
    }
  }
  if (event.type === "permission.asked") {
    return {
      type: "permission",
      sessionID: event.properties.sessionID,
      requestID: event.properties.id,
      open: true,
    }
  }
  if (event.type === "permission.replied") {
    return {
      type: "permission",
      sessionID: event.properties.sessionID,
      requestID: event.properties.requestID,
      open: false,
    }
  }
  if (event.type === "session.error" && event.properties.sessionID) {
    return {
      type: "error",
      sessionID: event.properties.sessionID,
      error: event.properties.error,
    }
  }
}
