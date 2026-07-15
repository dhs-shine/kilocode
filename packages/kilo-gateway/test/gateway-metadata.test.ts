import { describe, expect, test } from "bun:test"
import { streamText } from "ai"
import { createKilo } from "../src/provider"

const meta = {
  routing: {
    originalModelId: "anthropic/claude-fable-5",
    canonicalSlug: "anthropic/claude-opus-4.8",
    finalProvider: "anthropic",
    modelAttempts: [
      { canonicalSlug: "anthropic/claude-fable-5", success: false },
      { canonicalSlug: "anthropic/claude-opus-4.8", success: true },
    ],
  },
  cost: "0",
  marketCost: "0.140985",
  gatewayCost: "0",
  generationId: "gen_test",
}

const openai = {
  routing: {
    originalModelId: "openai/gpt-5.6-sol",
    canonicalSlug: "openai/gpt-5.6-sol",
    finalProvider: "openai",
    modelAttempts: [{ canonicalSlug: "openai/gpt-5.6-sol", success: true }],
  },
  cost: "0",
  marketCost: "0.16368525",
  gatewayCost: "0",
  generationId: "gen_openai",
}

function response(chunks: unknown[]) {
  const body = chunks.map((chunk) => `data: ${JSON.stringify(chunk)}\n\n`).join("") + "data: [DONE]\n\n"
  return new Response(body, {
    headers: { "content-type": "text/event-stream" },
  })
}

async function finish(result: ReturnType<typeof streamText>) {
  for await (const part of result.fullStream) {
    if (part.type === "finish-step") return part
  }
  throw new Error("missing finish-step")
}

describe("Kilo Gateway response metadata", () => {
  test("surfaces Anthropic terminal cost and routed model", async () => {
    const sdk = createKilo({
      kilocodeToken: "test",
      fetch: async () =>
        response([
          {
            type: "message_start",
            message: {
              id: "msg_test",
              model: "claude-fable-5",
              role: "assistant",
              usage: { input_tokens: 2, cache_creation_input_tokens: 0, cache_read_input_tokens: 0 },
            },
          },
          { type: "content_block_start", index: 0, content_block: { type: "text", text: "" } },
          { type: "content_block_delta", index: 0, delta: { type: "text_delta", text: "Hello" } },
          { type: "content_block_stop", index: 0 },
          {
            type: "message_delta",
            delta: { stop_reason: "end_turn", stop_sequence: null },
            usage: { input_tokens: 2, output_tokens: 1, cache_creation_input_tokens: 0 },
            provider_metadata: { anthropic: { usage: {} }, gateway: meta },
          },
          { type: "message_stop" },
        ]),
    })

    const part = await finish(streamText({ model: sdk.anthropic("anthropic/claude-fable-5"), prompt: "Hi" }))
    expect(part.response.modelId).toBe("anthropic/claude-opus-4.8")
    expect(part.providerMetadata?.gateway).toEqual(meta)
  })

  test("surfaces OpenAI Responses terminal cost and routed model", async () => {
    const sdk = createKilo({
      kilocodeToken: "test",
      fetch: async () =>
        response([
          {
            type: "response.created",
            response: { id: "resp_test", created_at: 0, model: "gpt-5.6-sol", service_tier: null },
          },
          {
            type: "response.completed",
            response: {
              incomplete_details: null,
              usage: {
                input_tokens: 2,
                input_tokens_details: { cached_tokens: 0 },
                output_tokens: 1,
                output_tokens_details: { reasoning_tokens: 0 },
              },
              service_tier: "default",
              provider_metadata: { openai: { responseId: "resp_test" }, gateway: openai },
            },
          },
        ]),
    })

    const part = await finish(streamText({ model: sdk.openai("openai/gpt-5.6-sol"), prompt: "Hi" }))
    expect(part.response.modelId).toBe("openai/gpt-5.6-sol")
    expect(part.providerMetadata?.gateway).toEqual(openai)
  })

  test("exposes raw chunks only when requested", async () => {
    const sdk = createKilo({
      kilocodeToken: "test",
      fetch: async () =>
        response([
          {
            type: "message_start",
            message: {
              id: "msg_test",
              model: "claude-fable-5",
              role: "assistant",
              usage: { input_tokens: 1, cache_creation_input_tokens: 0, cache_read_input_tokens: 0 },
            },
          },
          {
            type: "message_delta",
            delta: { stop_reason: "end_turn", stop_sequence: null },
            usage: { input_tokens: 1, output_tokens: 1 },
            provider_metadata: { gateway: meta },
          },
          { type: "message_stop" },
        ]),
    })
    const model = sdk.anthropic("anthropic/claude-fable-5")
    const hidden = await model.doStream({
      prompt: [{ role: "user", content: [{ type: "text", text: "Hi" }] }],
    })
    const parts = []
    for await (const part of hidden.stream) parts.push(part)
    expect(parts.some((part) => part.type === "raw")).toBe(false)

    const result = await model.doStream({
      prompt: [{ role: "user", content: [{ type: "text", text: "Hi" }] }],
      includeRawChunks: true,
    })
    const visible = []
    for await (const part of result.stream) visible.push(part)

    expect(visible.some((part) => part.type === "raw")).toBe(true)
  })
})
