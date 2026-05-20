import { createEffect, createMemo, createSignal } from "solid-js"
import type { AgentBuilderPreviewResponse } from "@kilocode/sdk/v2/client"
import { previewAgent, saveAgent, type AgentPayload, type Scope } from "../../../client"
import { useConfig } from "../../../context/config"
import { catalog, clean, sorted, text } from "../../../shared/utils"

type Mode = AgentPayload["mode"]
type Permission = NonNullable<AgentPayload["permission"]>
type Draft = AgentBuilderPreviewResponse

export const snippets = [
  "Review the current diff and report risks, regressions, and missing tests.",
  "Plan the work in small steps, then implement the smallest correct change.",
  "Inspect relevant files first, then summarize the root cause before editing.",
  "Run the smallest relevant validation checks and report any remaining failures.",
]

function maybe(input: string) {
  const value = clean(input)
  if (value) return value
  return undefined
}

function count(input: string) {
  const value = Number(input)
  if (Number.isInteger(value) && value > 0) return value
  return undefined
}

export function useAgentBuilder() {
  const ctx = useConfig()
  const snap = () => ctx.data()
  const [scope, setScope] = createSignal<Scope>(ctx.query()?.scope ?? "project")
  const [dirty, setDirty] = createSignal(false)
  const [id, setId] = createSignal("reviewer")
  const [desc, setDesc] = createSignal("")
  const [mode, setMode] = createSignal<Mode>("subagent")
  const [model, setModel] = createSignal("")
  const [color, setColor] = createSignal("")
  const [steps, setSteps] = createSignal("")
  const [tools, setTools] = createSignal<string[]>([])
  const [prompt, setPrompt] = createSignal("")
  const [permTool, setPermTool] = createSignal("bash")
  const [permPattern, setPermPattern] = createSignal("")
  const [permAction, setPermAction] = createSignal<"ask" | "allow" | "deny">("ask")
  const [permission, setPermission] = createSignal<Permission>({})
  const [draft, setDraft] = createSignal<Draft>()

  const all = createMemo(() => {
    const data = snap()
    if (!data) return []
    return catalog(data)
  })

  const picked = createMemo(() => new Set(tools()))
  const rules = createMemo(() =>
    Object.entries(permission()).flatMap(([tool, rule]) => {
      if (rule && typeof rule === "object" && !Array.isArray(rule)) {
        return Object.entries(rule as Record<string, unknown>).map(([pattern, act]) => ({
          tool,
          pattern,
          action: text(act),
        }))
      }
      return [{ tool, pattern: "*", action: text(rule) }]
    }),
  )

  createEffect(() => {
    if (dirty()) return
    const query = ctx.query()
    if (!query) return
    setScope(query.scope)
  })

  function updateScope(value: Scope) {
    setDirty(true)
    setScope(value)
  }

  function setToolList(value: string) {
    setTools(
      value
        .split(",")
        .map((word) => word.trim())
        .filter(Boolean),
    )
  }

  function toggleTool(id: string) {
    const values = new Set(tools())
    if (values.has(id)) values.delete(id)
    else values.add(id)
    setTools(sorted(values))
  }

  function addPermission() {
    const tool = clean(permTool())
    if (!tool) {
      ctx.fail("Enter an agent permission tool key before adding an override.")
      return
    }
    const next = { ...permission() }
    const pattern = clean(permPattern())
    if (!pattern) {
      next[tool] = permAction()
      setPermission(next)
      return
    }
    const cur = next[tool]
    const map: Record<string, unknown> = cur && typeof cur === "object" && !Array.isArray(cur) ? { ...cur } : {}
    map[pattern] = permAction()
    next[tool] = map
    setPermission(next)
  }

  function insert(value: string) {
    const cur = clean(prompt())
    setPrompt(cur ? `${cur}\n\n${value}` : value)
  }

  function build(): AgentPayload | undefined {
    const name = clean(id())
    const body = clean(prompt())
    if (!name || !body) {
      ctx.fail("Enter an agent id and prompt before previewing or saving.")
      return undefined
    }
    const rules = permission()
    return {
      scope: scope(),
      id: name,
      description: maybe(desc()),
      mode: mode(),
      model: maybe(model()),
      color: maybe(color()),
      steps: count(steps()),
      tools: tools().length ? tools() : undefined,
      permission: Object.keys(rules).length ? rules : undefined,
      prompt: body,
    }
  }

  function preview() {
    const payload = build()
    if (!payload) return
    ctx.run("Previewing agent", () => previewAgent(ctx.target(), payload).then(setDraft), { refetch: false })
  }

  function save() {
    const payload = build()
    if (!payload) return
    ctx.run("Saving agent", () => saveAgent(ctx.target(), payload).then(setDraft))
  }

  return {
    ctx,
    snap,
    all,
    scope,
    setScope: updateScope,
    id,
    setId,
    desc,
    setDesc,
    mode,
    setMode,
    model,
    setModel,
    color,
    setColor,
    steps,
    setSteps,
    tools,
    setTools: setToolList,
    prompt,
    setPrompt,
    permTool,
    setPermTool,
    permPattern,
    setPermPattern,
    permAction,
    setPermAction,
    draft,
    picked,
    rules,
    toggleTool,
    addPermission,
    insert,
    preview,
    save,
  }
}
