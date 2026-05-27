import { createKiloClient, type Config as EffectiveConfig } from "@kilocode/sdk/v2/client"
import type {
  AgentBuilderPreviewResponse,
  AgentBuilderSaveResponse,
  Auth,
  AppAgentsResponse,
  ConfigOverlayResponse,
  ConfigModelStateResponse,
  ConfigSourcesResponse,
  FormatterStatusResponse,
  GlobalHealthResponse,
  LspStatusResponse,
  McpStatusResponse,
  Pty as PtyInfo,
  Project as KiloProject,
  ProviderAuthAuthorization,
  ProviderAuthResponse,
  ProviderListResponse,
  ToolIdsResponse,
  ToolListResponse,
  TuiConfigGetResponse,
  VcsInfo,
  Worktree,
  WorktreeDiffItem,
} from "@kilocode/sdk/v2/client"

export type Scope = "global" | "project"

export type Query = {
  url: string
  dir: string
  scope: Scope
}

export type ProjectQuery = Pick<Query, "url" | "dir">

export type ProjectItem = KiloProject
export type RecentProjectItem = ProjectItem & {
  sessions: number
}

export type ProjectConsoleQuery = ProjectQuery & {
  project: string
}

export type ProjectConsoleSnapshot = {
  project: ProjectItem
  vcs: VcsInfo
  worktrees: string[]
  terminals: ProjectTerminalItem[]
}

export type ProjectWorktreeItem = Worktree
export type ProjectDiffItem = WorktreeDiffItem
export type ProjectPtyInfo = PtyInfo
export type ProjectTerminalItem = ProjectPtyInfo & {
  directory: string
}

export type Snapshot = {
  health: GlobalHealthResponse
  effective: EffectiveConfig
  overlay: ConfigOverlayResponse
  sources: ConfigSourcesResponse
  modelState: ConfigModelStateResponse
  providers: ProviderListResponse
  authMethods: ProviderAuthResponse
  tui: TuiConfigGetResponse
  tools: ToolIdsResponse
  toolDetails: ToolListResponse
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
const day = 24 * 60 * 60 * 1000
const hidden = new Set(["global"])
const key = "kilo.config.server"

const fetcher = window.fetch.bind(window) as typeof fetch

function encode(input: string) {
  const bytes = new TextEncoder().encode(input)
  return btoa(String.fromCharCode(...bytes))
}

function decode(input: string) {
  if (!input) return ""
  return decodeURIComponent(input)
}

function server(input: string) {
  const url = new URL(input)
  const user = decode(url.username) || "kilo"
  const pass = decode(url.password) || "kilo"
  url.username = ""
  url.password = ""
  url.search = ""
  url.hash = ""
  return {
    url: url.toString().replace(/\/$/, ""),
    token: encode(`${user}:${pass}`),
  }
}

function client(input: ProjectQuery) {
  const info = server(input.url)
  return createKiloClient({
    baseUrl: info.url,
    directory: value(input.dir),
    headers: {
      Authorization: `Basic ${info.token}`,
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

async function resolved(input: ProjectConsoleQuery) {
  const items = await loadVisibleProjects(input)
  const item = items.find((row) => row.id === input.project)
  if (!item) throw new Error(`Project not found: ${input.project}`)
  return item
}

async function maybe<T>(label: string, result: Promise<Result<T>>) {
  return await result
    .then((value) => {
      if (value.error) {
        console.warn(`${label}: ${message(value.error)}`)
        return undefined
      }
      return value.data
    })
    .catch((err) => {
      console.warn(`${label}: ${message(err)}`)
      return undefined
    })
}

function model(input: unknown) {
  if (typeof input !== "string") return undefined
  const index = input.indexOf("/")
  if (index <= 0 || index >= input.length - 1) return undefined
  return { provider: input.slice(0, index), model: input.slice(index + 1) }
}

async function probe(url: string) {
  const ctl = new AbortController()
  const timer = window.setTimeout(() => ctl.abort(), 400)
  const info = server(url)
  return await fetcher(`${info.url}/global/health`, { headers: { Authorization: `Basic ${info.token}` }, signal: ctl.signal })
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
  const [health, overlay, modelState, providers, authMethods, tui, tools, mcp, lsp, formatter, agents] = await Promise.all([
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
  ])
  const resolved = demand("Config overlay", overlay)
  const ref = model(resolved.effective.model)
  const info = ref ? await maybe("Tool metadata", sdk.tool.list(ref)) : undefined

  return {
    health: demand("Health", health),
    effective: resolved.effective,
    overlay: resolved,
    sources: { sources: resolved.sources },
    modelState: demand("Model state", modelState),
    providers: demand("Providers", providers),
    authMethods: demand("Provider auth methods", authMethods),
    tui: demand("TUI config", tui),
    tools: demand("Tools", tools),
    toolDetails: info ?? [],
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

export async function loadVisibleProjects(input: ProjectQuery): Promise<ProjectItem[]> {
  const items = await loadProjects(input)
  return items.filter((item) => !hidden.has(item.id))
}

export async function loadRecentProjects(input: ProjectQuery): Promise<RecentProjectItem[]> {
  const sdk = client(input)
  const [items, sessions] = await Promise.all([
    loadVisibleProjects(input),
    sdk.experimental.session.list({ archived: false, start: Date.now() - day, limit: 500 }),
  ])
  const rows = demand("Recent sessions", sessions)
  const counts = new Map<string, number>()

  for (const row of rows) {
    counts.set(row.projectID, (counts.get(row.projectID) ?? 0) + 1)
  }

  return items
    .map((item) => ({ ...item, sessions: counts.get(item.id) ?? 0 }))
    .filter((item) => item.sessions > 0)
}

export async function loadProjectConsole(input: ProjectConsoleQuery): Promise<ProjectConsoleSnapshot> {
  const project = await resolved(input)
  const query = { url: input.url, dir: project.worktree }
  const sdk = client(query)
  const [vcs, worktrees] = await Promise.all([
    sdk.vcs.get({ directory: query.dir }),
    sdk.worktree.list({ directory: query.dir }),
  ])
  const dirs = demand("Worktrees", worktrees)
  const terminals = await Promise.all([query.dir, ...dirs].map((dir) => loadProjectTerminals({ url: input.url, dir }, dir)))

  return {
    project,
    vcs: demand("VCS", vcs),
    worktrees: dirs,
    terminals: terminals.flat(),
  }
}

export async function createProjectWorktree(input: Query, name?: string): Promise<ProjectWorktreeItem> {
  const sdk = client(input)
  const trimmed = name?.trim()
  const result = await sdk.worktree.create({
    directory: input.dir,
    ...(trimmed ? { worktreeCreateInput: { name: trimmed } } : {}),
  })
  return demand("Create worktree", result)
}

export async function removeProjectWorktree(input: Query, dir: string) {
  const sdk = client(input)
  const result = await sdk.worktree.remove({ directory: input.dir, worktreeRemoveInput: { directory: dir } })
  return demand("Remove worktree", result)
}

export async function resetProjectWorktree(input: Query, dir: string) {
  const sdk = client(input)
  const result = await sdk.worktree.reset({ directory: input.dir, worktreeResetInput: { directory: dir } })
  return demand("Reset worktree", result)
}

export async function loadProjectTerminals(input: ProjectQuery, dir: string): Promise<ProjectTerminalItem[]> {
  const sdk = client({ url: input.url, dir })
  const result = await sdk.pty.list({ directory: dir })
  return demand("Terminals", result).map((item) => ({ ...item, directory: dir }))
}

export async function loadProjectDiff(input: Query, dir: string): Promise<ProjectDiffItem[]> {
  const sdk = client({ url: input.url, dir })
  const result = await sdk.worktree.diffSummary({ directory: dir })
  return demand("Worktree diff", result)
}

export async function loadProjectDiffFile(input: Query, dir: string, file: string): Promise<ProjectDiffItem | null> {
  const sdk = client({ url: input.url, dir })
  const result = await sdk.worktree.diffFile({ directory: dir, file })
  return demand("Worktree diff file", result)
}

export async function createProjectPty(input: Query, dir: string, title = "Kilo session"): Promise<ProjectPtyInfo> {
  const sdk = client({ url: input.url, dir })
  const result = await sdk.pty.create({ directory: dir, command: "kilo", cwd: dir, title })
  return demand("Create terminal", result)
}

export async function resizeProjectPty(input: Query, pty: string, cols: number, rows: number) {
  const sdk = client({ url: input.url, dir: input.dir })
  const result = await sdk.pty.update({ directory: input.dir, ptyID: pty, size: { cols, rows } })
  return demand("Resize terminal", result)
}

export function ptyWsUrl(input: Query, pty: string, cursor = 0) {
  const info = server(input.url)
  const url = new URL(`/pty/${encodeURIComponent(pty)}/connect`, info.url)
  url.protocol = url.protocol === "https:" ? "wss:" : "ws:"
  url.searchParams.set("directory", input.dir)
  url.searchParams.set("cursor", String(cursor))
  url.searchParams.set("auth_token", info.token)
  return url.toString()
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
