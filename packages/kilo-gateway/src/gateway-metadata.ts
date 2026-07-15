import type { LanguageModelV3 } from "@openrouter/ai-sdk-provider"

type Part = Awaited<ReturnType<LanguageModelV3["doStream"]>>["stream"] extends ReadableStream<infer Part> ? Part : never

type Json = null | string | number | boolean | Json[] | { [key: string]: Json | undefined }
type Gateway = { [key: string]: Json | undefined }
type Data = { [key: string]: Json | undefined }

function object(value: unknown): Data | undefined {
  if (!value || Array.isArray(value) || typeof value !== "object") return
  return value as Data
}

function gateway(value: unknown): Gateway | undefined {
  const raw = object(value)
  if (!raw) return
  const response = object(raw.response)
  const meta = raw.provider_metadata ?? response?.provider_metadata
  return object(object(meta)?.gateway)
}

function model(meta: Gateway) {
  const route = meta.routing
  if (!route || Array.isArray(route) || typeof route !== "object") return
  const attempts = Array.isArray(route.modelAttempts) ? route.modelAttempts : []
  const hit = attempts.findLast((item) => {
    if (!item || typeof item !== "object") return false
    return (item as Record<string, unknown>).success === true
  }) as Record<string, unknown> | undefined
  const id = hit?.canonicalSlug ?? route.canonicalSlug
  if (typeof id !== "string") return
  const value = id.trim()
  return value || undefined
}

function raw(value: unknown) {
  const item = object(value)
  if (!item || typeof item.type !== "string") return {}
  const response = object(item.response)
  const start = item.type === "message_start" ? object(item.message) : undefined
  const terminal = item.type === "response.completed" || item.type === "response.incomplete"
  const usage = object(terminal ? response?.usage : (item.usage ?? start?.usage))
  const id = terminal ? response?.model : start?.model
  return {
    usage,
    model: typeof id === "string" && id.trim() ? id.trim() : undefined,
    terminal,
  }
}

export function wrap(input: LanguageModelV3): LanguageModelV3 {
  return {
    specificationVersion: "v3",
    provider: input.provider,
    modelId: input.modelId,
    supportedUrls: input.supportedUrls,
    doGenerate: (options) => input.doGenerate(options),
    async doStream(options) {
      const result = await input.doStream({ ...options, includeRawChunks: true })
      const chunks = options.includeRawChunks === true
      let meta: Gateway | undefined
      let usage: Data | undefined
      let initial: string | undefined
      let terminal: string | undefined
      let current: string | undefined

      return {
        ...result,
        stream: result.stream.pipeThrough(
          new TransformStream<Part, Part>({
            transform(part, controller) {
              if (part.type === "raw") {
                meta = gateway(part.rawValue) ?? meta
                const info = raw(part.rawValue)
                usage = info.usage ? { ...usage, ...info.usage } : usage
                if (info.model) {
                  if (info.terminal) terminal = info.model
                  else initial = info.model
                }
                if (chunks) controller.enqueue(part)
                return
              }
              if (part.type === "response-metadata") current = part.modelId ?? current
              if (part.type !== "finish") {
                controller.enqueue(part)
                return
              }

              const id = (meta ? model(meta) : undefined) ?? terminal ?? initial
              if (id && id !== current) controller.enqueue({ type: "response-metadata", modelId: id })
              controller.enqueue({
                ...part,
                usage: usage
                  ? {
                      ...part.usage,
                      raw: { ...part.usage.raw, ...usage },
                    }
                  : part.usage,
                providerMetadata: meta ? { ...part.providerMetadata, gateway: meta } : part.providerMetadata,
              })
            },
          }),
        ),
      }
    },
  }
}
