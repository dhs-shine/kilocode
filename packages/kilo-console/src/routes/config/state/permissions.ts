import { createMemo, createSignal } from "solid-js"
import type { Snapshot } from "../../../client"
import { useConfig } from "../../../context/config"
import { clean, type PermissionMap } from "../../../shared/utils"

type Resolved = Snapshot["overlay"]["collections"][string][number]
export type PermissionAction = "ask" | "allow" | "deny"
export type PermissionTool = "external_directory" | "bash" | "read" | "edit"

export type PermissionRule = {
  tool: string
  pattern: string
  action: PermissionAction
  source: string
  inherited: boolean
  overridden: boolean
  path: string[]
}

export type PermissionGroup = {
  id: PermissionTool
  title: string
  description: string
  noun: string
  placeholder: string
  action: PermissionAction
  source: string
  inherited: boolean
  overridden: boolean
  rules: PermissionRule[]
}

export const actions: Array<{ value: PermissionAction; label: string }> = [
  { value: "ask", label: "Ask" },
  { value: "allow", label: "Allow" },
  { value: "deny", label: "Deny" },
]

export const defs: Array<{
  id: PermissionTool
  title: string
  description: string
  noun: string
  placeholder: string
}> = [
  {
    id: "external_directory",
    title: "External Directory",
    description: "Access files outside the project directory.",
    noun: "path",
    placeholder: "e.g. ~/Downloads/**",
  },
  {
    id: "bash",
    title: "Bash",
    description: "Run shell commands.",
    noun: "command",
    placeholder: "e.g. git status or npm *",
  },
  {
    id: "read",
    title: "Read",
    description: "Read files by matching file paths.",
    noun: "path",
    placeholder: "e.g. **/*.env",
  },
  {
    id: "edit",
    title: "Edit",
    description: "Modify files, including writes, patches, and multi-edits.",
    noun: "path",
    placeholder: "e.g. src/**/*.ts",
  },
]

const known = new Set<string>(defs.map((item) => item.id))

function act(input: unknown, fallback: PermissionAction = "ask"): PermissionAction {
  if (input === "ask" || input === "allow" || input === "deny") return input
  return fallback
}

function record(input: unknown): Record<string, unknown> {
  if (input && typeof input === "object" && !Array.isArray(input)) return input as Record<string, unknown>
  return {}
}

function cfg(data: Snapshot) {
  return record(data.effective.permission)
}

function meta(data: Snapshot, tool: string) {
  return data.overlay.collections.permission?.find((item) => item.key === tool)
}

function raw(data: Snapshot, tool: string) {
  if (typeof data.effective.permission === "string") return data.effective.permission
  return cfg(data)[tool]
}

function fallback(data: Snapshot) {
  return act(data.effective.permission)
}

function paths(item: Resolved | undefined, pattern: string) {
  if (!item) return ["permission", pattern]
  if (pattern === "*" && typeof item.value === "string") return item.path
  return [...item.path, pattern]
}

function row(tool: string, pattern: string, action: unknown, item?: Resolved): PermissionRule | undefined {
  if (action !== "ask" && action !== "allow" && action !== "deny") return undefined
  return {
    tool,
    pattern,
    action,
    source: item?.source ?? "default",
    inherited: item?.inherited ?? false,
    overridden: item?.overridden ?? false,
    path: item ? paths(item, pattern) : ["permission", tool, pattern],
  }
}

function entries(item: Resolved): PermissionRule[] {
  const rule = item.value
  if (rule && typeof rule === "object" && !Array.isArray(rule)) {
    return Object.entries(rule as Record<string, unknown>).flatMap(([glob, value]) => row(item.key, glob, value, item) ?? [])
  }
  const value = row(item.key, "*", rule, item)
  return value ? [value] : []
}

function listed(tool: string, value: unknown, item?: Resolved) {
  const obj = record(value)
  if (Object.keys(obj).length) {
    return Object.entries(obj).flatMap(([pattern, action]) => row(tool, pattern, action, item) ?? [])
  }
  const itemRow = row(tool, "*", value, item)
  return itemRow ? [itemRow] : []
}

function group(data: Snapshot, def: (typeof defs)[number]): PermissionGroup {
  const item = meta(data, def.id)
  const value = raw(data, def.id)
  const obj = record(value)
  const base = fallback(data)
  return {
    ...def,
    action: act(typeof value === "string" ? value : obj["*"], base),
    source: item?.source ?? "default",
    inherited: item?.inherited ?? false,
    overridden: item?.overridden ?? false,
    rules: Object.entries(obj)
      .filter(([pattern]) => pattern !== "*")
      .flatMap(([pattern, action]) => row(def.id, pattern, action, item) ?? []),
  }
}

export function usePermissionSettings() {
  const ctx = useConfig()
  const snap = () => ctx.data()
  const [mode, setMode] = createSignal<"closed" | "rule">("closed")
  const [kind, setKind] = createSignal<PermissionTool>("external_directory")
  const [pattern, setPattern] = createSignal("")
  const [action, setAction] = createSignal<PermissionAction>("ask")

  const rules = createMemo(() => (snap()?.overlay.collections.permission ?? []).flatMap(entries))
  const groups = createMemo(() => {
    const data = snap()
    if (!data) return []
    return defs.map((def) => group(data, def))
  })
  const other = createMemo(() => {
    const data = snap()
    if (!data) return []
    return Object.entries(cfg(data))
      .filter(([tool]) => !known.has(tool))
      .flatMap(([tool, value]) => listed(tool, value, meta(data, tool)))
      .sort((a, b) => a.tool.localeCompare(b.tool) || a.pattern.localeCompare(b.pattern))
  })
  const selected = createMemo(() => defs.find((def) => def.id === kind()) ?? defs[0])

  function open(tool: PermissionTool = "external_directory") {
    setKind(tool)
    setPattern("")
    setAction("ask")
    setMode("rule")
  }

  function close() {
    setMode("closed")
  }

  function choose(value: string) {
    const match = defs.find((def) => def.id === value)
    if (match) setKind(match.id)
  }

  function add() {
    const data = snap()
    const glob = clean(pattern())
    if (!data || !glob) {
      ctx.fail(`Enter a ${selected().noun} pattern before saving.`)
      return
    }
    const permission = { [kind()]: { [glob]: action() } } as PermissionMap
    ctx.save({ permission })
    close()
  }

  function setDefault(tool: PermissionTool, action: PermissionAction) {
    const permission = { [tool]: { "*": action } } as PermissionMap
    ctx.save({ permission })
  }

  function revert(rule: PermissionRule) {
    ctx.unset([rule.path])
  }

  return {
    ctx,
    mode,
    kind,
    choose,
    pattern,
    setPattern,
    action,
    setAction,
    rules,
    groups,
    other,
    selected,
    open,
    close,
    add,
    setDefault,
    revert,
  }
}
