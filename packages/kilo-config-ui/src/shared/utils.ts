import type { Provider } from "@kilocode/sdk/v2/client"
import type { Snapshot } from "../client"

export type McpMap = NonNullable<Snapshot["effective"]["mcp"]>
export type FormatterMap = Exclude<NonNullable<Snapshot["effective"]["formatter"]>, boolean>
export type LspMap = Exclude<NonNullable<Snapshot["effective"]["lsp"]>, boolean>
export type PermissionMap = Exclude<NonNullable<Snapshot["effective"]["permission"]>, string | null>

export function clean(input: string) {
  return input.trim()
}

export function text(input: unknown): string {
  if (input === undefined || input === null || input === "") return "Not set"
  if (typeof input === "string") return input
  if (typeof input === "number" || typeof input === "boolean") return String(input)
  return JSON.stringify(input) ?? "Unserializable value"
}

export function sorted(input: Iterable<string>) {
  return [...input].sort((a, b) => a.localeCompare(b))
}

export function words(input: string) {
  return input
    .split(/\s+/)
    .map((w) => w.trim())
    .filter(Boolean)
}

export function friendly(input: string) {
  return input
    .trim()
    .split(/[\s._/-]+/)
    .filter(Boolean)
    .map((part) => {
      if (/^[A-Z0-9]+$/.test(part) && part.length <= 4) return part
      return `${part[0]?.toUpperCase() ?? ""}${part.slice(1).toLowerCase()}`
    })
    .join(" ")
}

export function csv(input: string) {
  return input
    .split(",")
    .map((w) => w.trim())
    .filter(Boolean)
}

export function size(input: unknown) {
  if (!input || typeof input !== "object" || Array.isArray(input)) return 0
  return Object.keys(input).length
}

export function catalog(data: Snapshot) {
  return data.providers.all.flatMap((provider) =>
    Object.values(provider.models).map((model) => ({
      id: `${provider.id}/${model.id}`,
      provider,
      model,
    })),
  )
}

export function providerState(provider: Provider, data: Snapshot) {
  if (data.effective.disabled_providers?.includes(provider.id)) return "disabled"
  if (data.providers.failed.includes(provider.id)) return "failed"
  if (data.providers.connected.includes(provider.id)) return "connected"
  if (provider.source === "env" || provider.source === "config" || provider.source === "custom") return "configured"
  return "available"
}

export function fmtRecord(input: Snapshot["effective"]["formatter"]): FormatterMap {
  if (input && typeof input === "object") return input
  return {}
}

export function lspRecord(input: Snapshot["effective"]["lsp"]): LspMap {
  if (input && typeof input === "object") return input
  return {}
}

export function dupBindings(data: Snapshot, key: string, value: string) {
  if (!value) return []
  return Object.entries(data.tui.keybinds ?? {})
    .filter(([name, binding]) => name !== key && binding === value)
    .map(([name]) => name)
}

export function errMsg(input: unknown) {
  if (input instanceof Error) return input.message
  if (typeof input === "string") return input
  return text(input)
}

export function toScope(input: string | null): "global" | "project" {
  if (input === "global") return "global"
  return "project"
}

export function toMode(input: string): "primary" | "subagent" | "all" {
  if (input === "subagent") return "subagent"
  if (input === "all") return "all"
  return "primary"
}

export function toAction(input: string): "ask" | "allow" | "deny" {
  if (input === "allow") return "allow"
  if (input === "deny") return "deny"
  return "ask"
}
