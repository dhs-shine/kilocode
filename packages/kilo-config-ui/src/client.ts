import { createKiloClient, type Config as EffectiveConfig } from "@kilocode/sdk/v2/client"
import type {
  AgentBuilderPreviewResponse,
  AgentBuilderSaveResponse,
  Auth,
  AppAgentsResponse,
  ConfigOverlayResponse,
  ConfigModelStateResponse,
  ConfigRulesResponse,
  ConfigSourcesResponse,
  FormatterStatusResponse,
  GlobalHealthResponse,
  LspStatusResponse,
  McpStatusResponse,
  Project as KiloProject,
  ProviderAuthAuthorization,
  ProviderAuthResponse,
  ProviderListResponse,
  ToolIdsResponse,
  TuiConfigGetResponse,
} from "@kilocode/sdk/v2/client"

export type Scope = "global" | "project"

export type Query = {
  url: string
  dir: string
  scope: Scope
}

export type ProjectQuery = Pick<Query, "url" | "dir">

export type ProjectItem = KiloProject

export type Snapshot = {
  health: GlobalHealthResponse
  effective: EffectiveConfig
  overlay: ConfigOverlayResponse
  sources: ConfigSourcesResponse
  rules?: ConfigRulesResponse
  modelState: ConfigModelStateResponse
  providers: ProviderListResponse
  authMethods: ProviderAuthResponse
  tui: TuiConfigGetResponse
  tools: ToolIdsResponse
  mcp: McpStatusResponse
  lsp: LspStatusResponse
  formatter: FormatterStatusResponse
  agents: AppAgentsResponse
}

export type ConfigPatch = Partial<EffectiveConfig>

export type ConfigUnset = string[][]
export type ModelRef = ConfigModelStateResponse["favorite"][number]

export type TuiPatch = Partial<TuiConfigGetResponse>

export type AgentPayload = {
  scope: Scope
  id: string
  description?: string
  mode: "primary" | "subagent" | "all"
  model?: string
  color?: string
  steps?: number
  tools?: string[]
  permission?: Record<string, unknown>
  prompt: string
}

type Result<T> = {
  data: T | undefined
  error?: unknown
}

const ports = Array.from({ length: 20 }, (_, index) => 4097 + index)
const key = "kilo.config.server"
const auth = `Basic ${btoa("kilo:kilo")}`

const fetcher = window.fetch.bind(window) as typeof fetch

function client(input: ProjectQuery) {
  return createKiloClient({
    baseUrl: input.url,
    directory: value(input.dir),
    headers: {
      Authorization: auth,
    },
    fetch: fetcher,
  })
}

function value(input: string) {
  const trimmed = input.trim()
  if (trimmed) return trimmed
  return undefined
}

function message(input: unknown) {
  if (input instanceof Error) return input.message
  if (typeof input === "string") return input
  if (input === undefined || input === null) return "Unknown error"
  return JSON.stringify(input)
}

function demand<T>(label: string, result: Result<T>) {
  if (result.error) throw new Error(`${label}: ${message(result.error)}`)
  if (result.data === undefined) throw new Error(`${label}: empty response`)
  return result.data
}

async function probe(url: string) {
  const ctl = new AbortController()
  const timer = window.setTimeout(() => ctl.abort(), 400)
  return await fetcher(`${url}/global/health`, { headers: { Authorization: auth }, signal: ctl.signal })
    .then((res) => (res.ok ? url : undefined))
    .catch(() => undefined)
    .finally(() => window.clearTimeout(timer))
}

export function loadCached() {
  return window.localStorage.getItem(key) ?? ""
}

export function saveCached(url: string) {
  window.localStorage.setItem(key, url)
}

export function forgetCached() {
  window.localStorage.removeItem(key)
}

export async function healthy(url: string) {
  return (await probe(url)) !== undefined
}

export async function discover() {
  const urls = ports.flatMap((port) => [`http://127.0.0.1:${port}`, `http://localhost:${port}`])
  const hit = await Promise.any(
    urls.map((url) =>
      probe(url).then((value) => {
        if (value) return value
        throw new Error(`${url} unavailable`)
      }),
    ),
  ).catch(() => undefined)
  return hit
}

export async function load(input: Query): Promise<Snapshot> {
  const sdk = client(input)
  const [health, overlay, modelState, providers, authMethods, tui, tools, mcp, lsp, formatter, agents, rules] =
    await Promise.all([
      sdk.global.health(),
      sdk.config.overlay({ scope: input.scope }),
      sdk.config.modelState(),
      sdk.provider.list(),
      sdk.provider.auth(),
      sdk.tui.config.get(),
      sdk.tool.ids(),
      sdk.mcp.status(),
      sdk.lsp.status(),
      sdk.formatter.status(),
      sdk.app.agents(),
      input.scope === "project" ? sdk.config.rules() : Promise.resolve({ data: undefined }),
    ])
  const resolved = demand("Config overlay", overlay)

  return {
    health: demand("Health", health),
    effective: resolved.effective,
    overlay: resolved,
    sources: { sources: resolved.sources },
    rules: input.scope === "project" ? demand("Rules", rules) : undefined,
    modelState: demand("Model state", modelState),
    providers: demand("Providers", providers),
    authMethods: demand("Provider auth methods", authMethods),
    tui: demand("TUI config", tui),
    tools: demand("Tools", tools),
    mcp: demand("MCP status", mcp),
    lsp: demand("LSP status", lsp),
    formatter: demand("Formatter status", formatter),
    agents: demand("Agents", agents),
  }
}

export async function loadProjects(input: ProjectQuery): Promise<ProjectItem[]> {
  const sdk = client(input)
  const dir = value(input.dir)
  const result = await sdk.project.list(dir ? { directory: dir } : undefined)
  return demand("Projects", result)
}

export async function saveConfig(input: Query, patch: Partial<ConfigPatch>) {
  const sdk = client(input)
  const result = await sdk.config.overlayUpdate({ scope: input.scope, set: patch })
  return demand("Update config", result)
}

export async function unsetConfig(input: Query, unset: ConfigUnset) {
  const sdk = client(input)
  const result = await sdk.config.overlayUpdate({ scope: input.scope, unset })
  return demand("Update config", result)
}

export async function saveRules(input: Query, content: string) {
  const sdk = client(input)
  const result = await sdk.config.rulesUpdate({ content })
  return demand("Update rules", result)
}

export async function saveModelState(input: Query, favorite: ModelRef[]) {
  const sdk = client(input)
  const result = await sdk.config.modelStateUpdate({ favorite })
  return demand("Update model state", result)
}

export async function connectProvider(input: Query, id: string, key: string, metadata?: Record<string, string>) {
  const sdk = client(input)
  const auth: Auth = metadata ? { type: "api", key, metadata } : { type: "api", key }
  const result = await sdk.auth.set({ providerID: id, auth })
  demand("Connect provider", result)
  await sdk.global.dispose()
}

export async function authorizeProvider(
  input: Query,
  id: string,
  method: number,
  inputs?: Record<string, string>,
): Promise<ProviderAuthAuthorization> {
  const sdk = client(input)
  const result = await sdk.provider.oauth.authorize({ providerID: id, method, inputs })
  return demand("Authorize provider", result)
}

export async function completeProvider(input: Query, id: string, method: number, code?: string) {
  const sdk = client(input)
  const result = await sdk.provider.oauth.callback({ providerID: id, method, code })
  demand("Complete provider authorization", result)
  await sdk.global.dispose()
}

export async function saveTui(input: Query, patch: TuiPatch) {
  const sdk = client(input)
  const result = await sdk.tui.config.update({ scope: input.scope, ...patch })
  return demand("Update TUI config", result)
}

export async function previewAgent(input: Query, payload: AgentPayload): Promise<AgentBuilderPreviewResponse> {
  const sdk = client(input)
  const result = await sdk.agentBuilder.preview(payload)
  return demand("Preview agent", result)
}

export async function saveAgent(input: Query, payload: AgentPayload): Promise<AgentBuilderSaveResponse> {
  const sdk = client(input)
  const result = await sdk.agentBuilder.save({
    path_id: payload.id,
    scope: payload.scope,
    description: payload.description,
    mode: payload.mode,
    model: payload.model,
    color: payload.color,
    steps: payload.steps,
    tools: payload.tools,
    permission: payload.permission,
    prompt: payload.prompt,
  })
  return demand("Save agent", result)
}
