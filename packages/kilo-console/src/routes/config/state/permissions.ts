import { createMemo, createSignal } from "solid-js"
import type { Snapshot } from "../../../client"
import { useConfig } from "../../../context/config"
import { clean, text, type PermissionMap } from "../../../shared/utils"

type Resolved = Snapshot["overlay"]["collections"][string][number]

type Rule = {
  tool: string
  pattern: string
  action: string
  source: string
  inherited: boolean
  overridden: boolean
  path: string[]
}

function entries(item: Resolved): Rule[] {
  const rule = item.value
  if (rule && typeof rule === "object" && !Array.isArray(rule)) {
    return Object.entries(rule as Record<string, unknown>).map(([glob, act]) => ({
      tool: item.key,
      pattern: glob,
      action: text(act),
      source: item.source,
      inherited: item.inherited,
      overridden: item.overridden,
      path: [...item.path, glob],
    }))
  }
  return [
    {
      tool: item.key,
      pattern: "*",
      action: text(rule),
      source: item.source,
      inherited: item.inherited,
      overridden: item.overridden,
      path: item.path,
    },
  ]
}

export function usePermissionSettings() {
  const ctx = useConfig()
  const snap = () => ctx.data()
  const [tool, setTool] = createSignal("bash")
  const [pattern, setPattern] = createSignal("")
  const [action, setAction] = createSignal<"ask" | "allow" | "deny">("ask")

  const rules = createMemo(() => (snap()?.overlay.collections.permission ?? []).flatMap(entries))

  function add() {
    const data = snap()
    const id = clean(tool())
    if (!data || !id) {
      ctx.fail("Enter a permission tool key before saving.")
      return
    }
    const glob = clean(pattern())
    const permission: PermissionMap = glob ? { [id]: { [glob]: action() } } : { [id]: action() }
    ctx.save({ permission })
  }

  function revert(rule: Rule) {
    ctx.unset([rule.path])
  }

  return { ctx, tool, setTool, pattern, setPattern, action, setAction, rules, add, revert }
}
