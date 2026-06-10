import { describe, expect, test } from "bun:test"
import { streamText, tool } from "ai"
import { z } from "zod"
import { buildRequestHeaders, createKilo } from "../src/provider"

describe("Kilo provider request headers", () => {
  test("request headers override provider defaults", () => {
    const headers = buildRequestHeaders(
      {
        "content-type": "application/json",
        "x-kilocode-feature": "vscode-extension",
        "x-default-only": "kept",
      },
      {
        "x-kilocode-feature": "agent-manager",
        "x-request-only": "kept-too",
      },
    )

    expect(headers.get("content-type")).toBe("application/json")
    expect(headers.get("x-kilocode-feature")).toBe("agent-manager")
    expect(headers.get("x-default-only")).toBe("kept")
    expect(headers.get("x-request-only")).toBe("kept-too")
  })
})

describe("Kilo provider tool streaming", () => {
  test("executes a tool once when complete arguments are followed by whitespace", async () => {
    const chunks = [
      {
        choices: [
          {
            index: 0,
            delta: {
              role: "assistant",
              content: null,
              tool_calls: [
                {
                  index: 0,
                  id: "call_question",
                  type: "function",
                  function: { name: "question", arguments: '{"answer":"lasagna"}' },
                },
              ],
            },
            logprobs: null,
            finish_reason: null,
          },
        ],
      },
      {
        choices: [
          {
            index: 0,
            delta: {
              tool_calls: [{ index: 0, function: { arguments: " " } }],
            },
            logprobs: null,
            finish_reason: null,
          },
        ],
      },
      {
        choices: [{ index: 0, delta: {}, logprobs: null, finish_reason: "tool_calls" }],
      },
      {
        choices: [],
        usage: { prompt_tokens: 10, completion_tokens: 20, total_tokens: 30 },
      },
    ].map((chunk) =>
      JSON.stringify({
        id: "chatcmpl-question",
        object: "chat.completion.chunk",
        created: 1711357598,
        model: "qwen/qwen3.6-plus",
        ...chunk,
      }),
    )
    const body = [...chunks.map((chunk) => `data: ${chunk}\n\n`), "data: [DONE]\n\n"].join("")
    const fetcher = async () =>
      new Response(body, {
        status: 200,
        headers: { "content-type": "text/event-stream" },
      })
    const provider = createKilo({
      apiKey: "test",
      baseURL: "https://gateway.test/api/openrouter/",
      fetch: fetcher as typeof fetch,
    })
    const calls: Array<{ answer: string }> = []
    const result = streamText({
      model: provider.languageModel("qwen/qwen3.6-plus"),
      prompt: "Ask a question",
      tools: {
        question: tool({
          description: "Ask the user a question",
          inputSchema: z.object({ answer: z.string() }),
          execute: async (input) => {
            calls.push(input)
            return input.answer
          },
        }),
      },
    })
    const events = []
    for await (const event of result.fullStream) events.push(event)

    expect(events.filter((event) => event.type === "tool-call")).toHaveLength(1)
    expect(calls).toEqual([{ answer: "lasagna" }])
  })
})
