import { describe, expect, test } from "bun:test"
import type { LanguageModelV3Prompt, LanguageModelV3StreamPart } from "@ai-sdk/provider"
import { OpenAIResponsesLanguageModel } from "../../../src/provider/sdk/copilot/responses/openai-responses-language-model"

const prompt: LanguageModelV3Prompt = [{ role: "user", content: [{ type: "text", text: "Hello" }] }]

function stream(chunks: unknown[]) {
  return new ReadableStream({
    start(controller) {
      for (const chunk of chunks) {
        controller.enqueue(new TextEncoder().encode(`data: ${JSON.stringify(chunk)}\n\n`))
      }
      controller.close()
    },
  })
}

function model(chunks: unknown[]) {
  const fetch: typeof globalThis.fetch = Object.assign(
    async () =>
      new Response(stream(chunks), {
        status: 200,
        headers: { "Content-Type": "text/event-stream" },
      }),
    { preconnect: () => {} },
  )

  return new OpenAIResponsesLanguageModel("test-model", {
    provider: "test",
    url: () => "https://api.test.com/responses",
    headers: () => ({}),
    fetch,
  })
}

async function parts(chunks: unknown[]) {
  const result = await model(chunks).doStream({ prompt, includeRawChunks: false })
  const out: LanguageModelV3StreamPart[] = []
  for await (const part of result.stream) out.push(part)
  return out
}

describe("OpenAI Responses finish mapping", () => {
  test("treats a completed tool call without a terminal response chunk as tool-calls", async () => {
    const out = await parts([
      {
        type: "response.created",
        response: { id: "resp_test", created_at: 1, model: "test-model", service_tier: null },
      },
      {
        type: "response.output_item.added",
        output_index: 0,
        item: { type: "function_call", id: "fc_1", call_id: "call_1", name: "todowrite", arguments: "" },
      },
      {
        type: "response.function_call_arguments.delta",
        item_id: "fc_1",
        output_index: 0,
        delta: "{}",
      },
      {
        type: "response.output_item.done",
        output_index: 0,
        item: {
          type: "function_call",
          id: "fc_1",
          call_id: "call_1",
          name: "todowrite",
          arguments: "{}",
          status: "completed",
        },
      },
    ])

    expect(out.at(-1)).toMatchObject({ type: "finish", finishReason: { unified: "tool-calls" } })
  })

  test("treats a text stream without a terminal response chunk as stop", async () => {
    const out = await parts([
      {
        type: "response.created",
        response: { id: "resp_test", created_at: 1, model: "test-model", service_tier: null },
      },
      {
        type: "response.output_item.added",
        output_index: 0,
        item: { type: "message", id: "msg_1" },
      },
      {
        type: "response.output_text.delta",
        item_id: "msg_1",
        delta: "Hello",
        logprobs: null,
      },
      {
        type: "response.output_item.done",
        output_index: 0,
        item: { type: "message", id: "msg_1" },
      },
    ])

    expect(out.at(-1)).toMatchObject({ type: "finish", finishReason: { unified: "stop" } })
  })
})
