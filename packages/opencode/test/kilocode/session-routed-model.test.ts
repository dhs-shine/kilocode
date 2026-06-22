import { describe, expect, test } from "bun:test"
import { Effect } from "effect"
import { LLMAISDK } from "../../src/session/llm/ai-sdk"

describe("session routed model", () => {
  type Event = Parameters<typeof LLMAISDK.toLLMEvents>[1]

  const adapt = (events: ReadonlyArray<Event>) => {
    const state = LLMAISDK.adapterState()
    return Effect.runPromise(
      Effect.forEach(events, (event) => LLMAISDK.toLLMEvents(state, event)).pipe(Effect.map((items) => items.flat())),
    )
  }
  const unchecked = (input: unknown) => input as Event

  test("preserves finish-step response model in provider metadata", async () => {
    const events = await adapt([
      unchecked({
        type: "finish-step",
        response: { id: "response-1", timestamp: new Date(0), modelId: "openai/gpt-5.5-20260423" },
        finishReason: "stop",
        rawFinishReason: "stop",
        usage: {},
        providerMetadata: { openrouter: { routed: true }, kilocode: { existing: true } },
      }),
    ])

    expect(events).toHaveLength(1)
    const event = events[0]
    if (event.type !== "step-finish") throw new Error("expected step-finish")
    expect(event.providerMetadata).toEqual({
      openrouter: { routed: true },
      kilocode: { existing: true, routedModelID: "openai/gpt-5.5-20260423" },
    })
  })

  test("leaves finish-step metadata unchanged without a response model", async () => {
    const meta = { openrouter: { routed: true } }
    const events = await adapt([
      unchecked({
        type: "finish-step",
        response: { id: "response-1", timestamp: new Date(0) },
        finishReason: "stop",
        rawFinishReason: "stop",
        usage: {},
        providerMetadata: meta,
      }),
    ])

    expect(events).toHaveLength(1)
    const event = events[0]
    if (event.type !== "step-finish") throw new Error("expected step-finish")
    expect(event.providerMetadata).toEqual(meta)
  })
})
