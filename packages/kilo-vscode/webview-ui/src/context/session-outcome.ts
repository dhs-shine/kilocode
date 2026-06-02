import type { Message, Part, SessionCloseReason, TodoItem } from "../types/messages"
import { snapshotOnlyAssistant } from "./session-utils"

type TerminalKind = "incomplete" | "limit" | "unknown" | "filtered" | "unexpected" | "interrupted" | "error"
type TerminalTone = "warning" | "critical"

export interface TerminalState {
  kind: TerminalKind
  tone: TerminalTone
  finish?: string
  remaining: number
}

interface Input {
  reason?: SessionCloseReason
  messages: Message[]
  todos: TodoItem[]
  parts?: (msg: Message) => Part[] | undefined
  hidden?: (id: string) => boolean
}

function last(input: Input): Message | undefined {
  for (let i = input.messages.length - 1; i >= 0; i -= 1) {
    const msg = input.messages[i]
    if (!msg) continue
    if (msg.role !== "assistant") return undefined
    const parts = input.parts?.(msg) ?? msg.parts
    if (snapshotOnlyAssistant(msg, parts)) continue
    return msg
  }
  return undefined
}

export function terminal(input: Input): TerminalState | undefined {
  if (!input.reason) return undefined
  const msg = last(input)
  const finish = msg?.finish
  const remaining = input.todos.filter((item) => item.status !== "completed" && item.status !== "cancelled").length

  if (input.reason === "interrupted") return { kind: "interrupted", tone: "warning", finish, remaining }
  if (input.reason === "error") {
    if (msg?.error && !input.hidden?.(msg.id)) return undefined
    return { kind: "error", tone: "critical", finish, remaining }
  }
  if (finish === "length") return { kind: "limit", tone: "warning", finish, remaining }
  if (finish === "unknown") return { kind: "unknown", tone: "warning", finish, remaining }
  if (finish === "content-filter") return { kind: "filtered", tone: "warning", finish, remaining }
  if (finish === "other") return { kind: "unexpected", tone: "warning", finish, remaining }
  if (remaining > 0) return { kind: "incomplete", tone: "warning", finish, remaining }
  return undefined
}
