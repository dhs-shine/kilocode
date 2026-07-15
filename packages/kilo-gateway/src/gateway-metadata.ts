import type { LanguageModelV3 } from "@openrouter/ai-sdk-provider"

type Part = Awaited<ReturnType<LanguageModelV3["doStream"]>>["stream"] extends ReadableStream<infer Part> ? Part : never

type Json = null | string | number | boolean | Json[] | { [key: string]: Json | undefined }
type Gateway = { [key: string]: Json | undefined }

function gateway(value: unknown): Gateway | undefined {
  if (!value || typeof value !== "object") return
  const raw = value as Record<string, unknown>
  const response =
    raw.response && typeof raw.response === "object" ? (raw.response as Record<string, unknown>) : undefined
  const meta = raw.provider_metadata ?? response?.provider_metadata
  if (!meta || typeof meta !== "object") return
  const item = (meta as Record<string, unknown>).gateway
  if (!item || typeof item !== "object") return
  return item as Gateway
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

export function wrap(input: LanguageModelV3): LanguageModelV3 {
  return {
    specificationVersion: "v3",
    provider: input.provider,
    modelId: input.modelId,
    supportedUrls: input.supportedUrls,
    doGenerate: (options) => input.doGenerate(options),
    async doStream(options) {
      const result = await input.doStream({ ...options, includeRawChunks: true })
      const raw = options.includeRawChunks === true
      let meta: Gateway | undefined

      return {
        ...result,
        stream: result.stream.pipeThrough(
          new TransformStream<Part, Part>({
            transform(part, controller) {
              if (part.type === "raw") {
                meta = gateway(part.rawValue) ?? meta
                if (raw) controller.enqueue(part)
                return
              }
              if (part.type !== "finish" || !meta) {
                controller.enqueue(part)
                return
              }

              const id = model(meta)
              if (id) controller.enqueue({ type: "response-metadata", modelId: id })
              controller.enqueue({
                ...part,
                providerMetadata: {
                  ...part.providerMetadata,
                  gateway: meta,
                },
              })
            },
          }),
        ),
      }
    },
  }
}
