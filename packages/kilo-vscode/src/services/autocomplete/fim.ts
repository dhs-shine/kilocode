import { ResponseMetaData } from "./types"
import type { KiloConnectionService } from "../cli-backend"
import { getAutocompleteModel } from "../../shared/autocomplete-models"

const FIM_MAX_TOKENS = 256
const MISTRAL_FIM_URL = "https://api.mistral.ai/v1/fim/completions"
const CODESTRAL_FIM_URL = "https://codestral.mistral.ai/v1/fim/completions"
const INCEPTION_FIM_URL = "https://api.inceptionlabs.ai/v1/fim/completions"

type FimProvider = "mistral" | "inception"

interface DirectFimTarget {
  provider: FimProvider
  model: string
  urls: string[]
}

interface ProviderItem {
  id: string
  key?: string
  options?: Record<string, unknown>
}

interface ProviderListResponse {
  all: ProviderItem[]
}

interface DirectFimChunk {
  choices?: Array<{
    delta?: { content?: string }
    text?: string
  }>
  usage?: {
    prompt_tokens?: number
    completion_tokens?: number
  }
}

interface DirectFimOptions {
  apiKey: string
  target: DirectFimTarget
  prefix: string
  suffix: string
  temperature: number
  onChunk: (text: string) => void
  signal?: AbortSignal
  fetchImpl?: typeof fetch
}

export function getDirectFimTarget(model: string): DirectFimTarget | null {
  const info = getAutocompleteModel(model)
  if (info.directProvider === "mistral") {
    return { provider: "mistral", model: info.requestModel, urls: [MISTRAL_FIM_URL, CODESTRAL_FIM_URL] }
  }
  if (info.directProvider === "inception") {
    return { provider: "inception", model: info.requestModel, urls: [INCEPTION_FIM_URL] }
  }
  return null
}

async function resolveProviderKey(connectionService: KiloConnectionService, provider: FimProvider): Promise<string | null> {
  const client = await connectionService.getClientAsync()
  const result = await client.provider.list({}, { throwOnError: true })
  const data = result.data as ProviderListResponse | undefined
  const item = data?.all.find((p) => p.id === provider)
  const key = item?.options?.apiKey
  return item?.key ?? (typeof key === "string" ? key : null)
}

function extractDirectFimContent(chunk: DirectFimChunk): string {
  const choice = chunk.choices?.[0]
  return choice?.delta?.content ?? choice?.text ?? ""
}

function parseDirectFimEvent(data: string): DirectFimChunk | null {
  if (data === "[DONE]") return null
  return JSON.parse(data) as DirectFimChunk
}

function parseDirectFimLine(line: string): string | null {
  const trimmed = line.trim()
  if (!trimmed.startsWith("data:")) return null
  return trimmed.slice("data:".length).trim()
}

function handleDirectFimLine(
  line: string,
  onChunk: (text: string) => void,
  usage: { inputTokens: number; outputTokens: number },
) {
  const data = parseDirectFimLine(line)
  if (!data) return
  const event = parseDirectFimEvent(data)
  if (!event) return
  const content = extractDirectFimContent(event)
  if (content) onChunk(content)
  usage.inputTokens = event.usage?.prompt_tokens ?? usage.inputTokens
  usage.outputTokens = event.usage?.completion_tokens ?? usage.outputTokens
}

export async function generateDirectFim(options: DirectFimOptions): Promise<ResponseMetaData> {
  const urls = [...options.target.urls]
  const [url] = urls
  if (!url) throw new Error("FIM request failed: 500 missing provider endpoint")
  return generateDirectFimWithUrl(options, url, urls.slice(1))
}

async function generateDirectFimWithUrl(
  options: DirectFimOptions,
  url: string,
  fallbacks: string[],
): Promise<ResponseMetaData> {
  const fetchImpl = options.fetchImpl ?? fetch
  console.info(`[FIM] request provider=${options.target.provider} model=${options.target.model} url=${url}`)
  const res = await fetchImpl(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${options.apiKey}`,
    },
    body: JSON.stringify({
      model: options.target.model,
      prompt: options.prefix,
      suffix: options.suffix,
      max_tokens: FIM_MAX_TOKENS,
      temperature: options.temperature,
      stream: true,
    }),
    signal: options.signal,
  })

  if (!res.ok) {
    const body = await res.text().catch(() => "")
    const [next] = fallbacks
    if (res.status === 401 && next) return generateDirectFimWithUrl(options, next, fallbacks.slice(1))
    throw new Error(`FIM request failed: ${res.status} ${res.statusText}: ${body}`)
  }

  if (!res.body) throw new Error("FIM request failed: 500 empty response body")

  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  const usage = { inputTokens: 0, outputTokens: 0 }
  let pending = ""

  while (true) {
    const chunk = await reader.read()
    if (chunk.done) break
    pending += decoder.decode(chunk.value, { stream: true })
    const lines = pending.split("\n")
    pending = lines.pop() ?? ""

    for (const line of lines) {
      handleDirectFimLine(line, options.onChunk, usage)
    }
  }

  pending += decoder.decode()
  handleDirectFimLine(pending, options.onChunk, usage)

  reader.releaseLock()

  return {
    cost: 0,
    inputTokens: usage.inputTokens,
    outputTokens: usage.outputTokens,
    cacheWriteTokens: 0,
    cacheReadTokens: 0,
  }
}

/**
 * Generate a FIM (Fill-in-the-Middle) completion via the CLI backend.
 * Uses the SDK's kilo.fim() SSE endpoint which handles auth and streaming.
 *
 * @param signal - Optional AbortSignal to cancel the SSE stream early (e.g. when the user types again)
 */
export async function generateFim(
  connectionService: KiloConnectionService,
  modelId: string,
  prefix: string,
  suffix: string,
  onChunk: (text: string) => void,
  signal?: AbortSignal,
): Promise<ResponseMetaData> {
  const client = await connectionService.getClientAsync()
  const info = getAutocompleteModel(modelId)
  const target = getDirectFimTarget(modelId)
  const key = info.directProvider ? await resolveProviderKey(connectionService, info.directProvider).catch(() => null) : null

  if (target && !key) {
    throw new Error(`FIM request failed: 401 Missing ${target.provider} provider API key`)
  }

  if (target && key) {
    return generateDirectFim({
      apiKey: key,
      target,
      prefix,
      suffix,
      temperature: info.temperature,
      onChunk,
      signal,
    })
  }

  let cost = 0
  let inputTokens = 0
  let outputTokens = 0

  // Capture SSE-level errors so they propagate to the caller. The SDK's SSE
  // client catches HTTP errors (402, 401, 429, 5xx) internally and silently
  // ends the stream. Without this, errors never reach ErrorBackoff.
  let sseError: Error | undefined

  const temp = info.temperature
  console.info(`[FIM] request provider=kilo model=${info.requestModel} url=/kilo/fim`)

  const { stream } = await client.kilo.fim(
    {
      prefix,
      suffix,
      model: info.requestModel,
      maxTokens: FIM_MAX_TOKENS,
      temperature: temp,
    },
    {
      signal,
      sseMaxRetryAttempts: 1,
      onSseError: (error) => {
        sseError = error instanceof Error ? error : new Error(String(error))
      },
    },
  )

  for await (const chunk of stream) {
    const choice = chunk.choices?.[0]
    const content = choice?.delta?.content ?? choice?.text
    if (content) onChunk(content)
    if (chunk.usage) {
      inputTokens = chunk.usage.prompt_tokens ?? 0
      outputTokens = chunk.usage.completion_tokens ?? 0
    }
    if (chunk.cost !== undefined) cost = chunk.cost
  }

  if (sseError) throw sseError

  return {
    cost,
    inputTokens,
    outputTokens,
    cacheWriteTokens: 0,
    cacheReadTokens: 0,
  }
}

/**
 * Check if the CLI backend is connected. The CLI manages credentials internally,
 * so a connected state means we can issue FIM requests.
 */
export function hasValidCredentials(connectionService: KiloConnectionService): boolean {
  return connectionService.getConnectionState() === "connected"
}
