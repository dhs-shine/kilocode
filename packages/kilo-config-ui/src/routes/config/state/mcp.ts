import { createMemo, createSignal } from "solid-js"
import type { Snapshot } from "../../../client"
import { useConfig } from "../../../context/config"
import { clean, words, type McpMap } from "../../../shared/utils"

type Resolved = Snapshot["overlay"]["collections"][string][number]

function record(input: unknown) {
  if (input && typeof input === "object" && !Array.isArray(input)) return input as Record<string, unknown>
  return {}
}

export function useMcpSettings() {
  const ctx = useConfig()
  const snap = () => ctx.data()
  const [name, setName] = createSignal("")
  const [value, setValue] = createSignal("")
  const mcp = createMemo(() => snap()?.overlay.collections.mcp ?? [])

  function add() {
    const data = snap()
    const id = clean(name())
    const input = clean(value())
    if (!data || !id || !input) {
      ctx.fail("Enter an MCP name and a command or URL before saving.")
      return
    }
    const cfg =
      input.startsWith("http://") || input.startsWith("https://")
        ? { type: "remote" as const, url: input, enabled: true }
        : { type: "local" as const, command: words(input), enabled: true }
    ctx.save({ mcp: { [id]: cfg } })
  }

  function toggle(item: Resolved) {
    const cfg = record(item.local ?? item.value)
    const enabled = cfg.enabled !== false
    if (item.inherited && enabled) {
      ctx.save({ mcp: { [item.key]: { enabled: false } } })
      return
    }
    ctx.save({ mcp: { [item.key]: { ...cfg, enabled: !enabled } } as McpMap })
  }

  function revert(item: Resolved) {
    ctx.unset([item.path])
  }

  return { ctx, snap, name, setName, value, setValue, mcp, add, toggle, revert }
}
