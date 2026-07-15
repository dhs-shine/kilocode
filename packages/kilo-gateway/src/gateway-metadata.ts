import type { LanguageModelV3 } from "@openrouter/ai-sdk-provider"
import { z } from "zod"

type Part = Awaited<ReturnType<LanguageModelV3["doStream"]>>["stream"] extends ReadableStream<infer Part> ? Part : never

const dataSchema = z.record(z.string(), z.json())
const attemptSchema = z
  .object({
    canonicalSlug: z.string().optional(),
    success: z.boolean().optional(),
  })
  .catchall(z.json())
const routingSchema = z
  .object({
    canonicalSlug: z.string().optional(),
    modelAttempts: z.array(attemptSchema).optional(),
  })
  .catchall(z.json())
const gatewaySchema = z
  .object({
    routing: routingSchema.optional(),
  })
  .catchall(z.json())
const metadataSchema = z.object({ gateway: gatewaySchema.optional() })
const gatewayEventSchema = z.object({
  provider_metadata: metadataSchema.optional(),
  response: z.object({ provider_metadata: metadataSchema.optional() }).optional(),
})
const rawEventSchema = z.discriminatedUnion("type", [
  z.object({
    type: z.literal("message_start"),
    message: z.object({
      model: z.string().optional(),
      usage: dataSchema.optional(),
    }),
  }),
  z.object({
    type: z.literal("message_delta"),
    usage: dataSchema.optional(),
  }),
  z.object({
    type: z.enum(["response.completed", "response.incomplete"]),
    response: z.object({
      model: z.string().optional(),
      usage: dataSchema.optional(),
    }),
  }),
])

type Gateway = z.infer<typeof gatewaySchema>
type Data = z.infer<typeof dataSchema>

function gateway(value: unknown): Gateway | undefined {
  const result = gatewayEventSchema.safeParse(value)
  if (!result.success) return
  return result.data.provider_metadata?.gateway ?? result.data.response?.provider_metadata?.gateway
}

function model(meta: Gateway) {
  const route = meta.routing
  if (!route) return
  const hit = route.modelAttempts?.findLast((item) => item.success === true)
  const id = hit?.canonicalSlug ?? route.canonicalSlug
  if (!id) return
  const value = id.trim()
  return value || undefined
}

function raw(value: unknown) {
  const result = rawEventSchema.safeParse(value)
  if (!result.success) return {}
  const item = result.data
  switch (item.type) {
    case "message_start":
      return {
        usage: item.message.usage,
        model: item.message.model?.trim() || undefined,
        terminal: false,
      }
    case "message_delta":
      return { usage: item.usage, terminal: false }
    case "response.completed":
    case "response.incomplete":
      return {
        usage: item.response.usage,
        model: item.response.model?.trim() || undefined,
        terminal: true,
      }
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
