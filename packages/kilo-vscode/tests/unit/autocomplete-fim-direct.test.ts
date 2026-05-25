import { describe, expect, it } from "bun:test"
import { generateDirectFim, getDirectFimTarget } from "../../src/services/autocomplete/fim"

function stream(text: string) {
  return new ReadableStream<Uint8Array>({
    start(controller) {
      controller.enqueue(new TextEncoder().encode(text))
      controller.close()
    },
  })
}

describe("direct autocomplete FIM", () => {
  it("maps autocomplete models to direct provider endpoints", () => {
    expect(getDirectFimTarget("mistralai/codestral-2508")).toBeNull()
    expect(getDirectFimTarget("inception/mercury-edit-2")).toBeNull()
    expect(getDirectFimTarget("mistral/codestral-2508")).toEqual({
      provider: "mistral",
      model: "codestral-2508",
      urls: ["https://api.mistral.ai/v1/fim/completions", "https://codestral.mistral.ai/v1/fim/completions"],
    })
    expect(getDirectFimTarget("inception-direct/mercury-edit-2")).toEqual({
      provider: "inception",
      model: "mercury-edit-2",
      urls: ["https://api.inceptionlabs.ai/v1/fim/completions"],
    })
    expect(getDirectFimTarget("openai/gpt-5")).toBeNull()
  })

  it("streams provider FIM chunks and returns usage", async () => {
    const chunks = [
      'data: {"choices":[{"delta":{"content":"hel"}}]}\n\n',
      'data: {"choices":[{"delta":{"content":"lo"}}],"usage":{"prompt_tokens":3,"completion_tokens":2}}\n\n',
      "data: [DONE]\n\n",
    ].join("")
    const calls: RequestInit[] = []
    const fetchImpl: typeof fetch = async (_url, init) => {
      calls.push(init ?? {})
      return new Response(stream(chunks), { status: 200 })
    }
    const text: string[] = []
    const usage = await generateDirectFim({
      apiKey: "test-key",
      target: getDirectFimTarget("inception-direct/mercury-edit-2")!,
      prefix: "const value = ",
      suffix: "\n",
      temperature: 0,
      onChunk: (chunk) => text.push(chunk),
      fetchImpl,
    })

    expect(text.join("")).toBe("hello")
    expect(usage).toEqual({
      cost: 0,
      inputTokens: 3,
      outputTokens: 2,
      cacheWriteTokens: 0,
      cacheReadTokens: 0,
    })
    expect(calls[0]?.headers).toEqual({
      "Content-Type": "application/json",
      Authorization: "Bearer test-key",
    })
    expect(JSON.parse(String(calls[0]?.body))).toEqual({
      model: "mercury-edit-2",
      prompt: "const value = ",
      suffix: "\n",
      max_tokens: 256,
      temperature: 0,
      stream: true,
    })
  })

  it("retries Codestral-specific endpoint when Mistral rejects a Codestral key", async () => {
    const urls: string[] = []
    const fetchImpl: typeof fetch = async (url) => {
      urls.push(String(url))
      if (urls.length === 1) return new Response("unauthorized", { status: 401, statusText: "Unauthorized" })
      return new Response(stream("data: [DONE]\n\n"), { status: 200 })
    }

    await generateDirectFim({
      apiKey: "codestral-key",
      target: getDirectFimTarget("mistral/codestral-2508")!,
      prefix: "",
      suffix: "",
      temperature: 0.2,
      onChunk: () => {},
      fetchImpl,
    })

    expect(urls).toEqual(["https://api.mistral.ai/v1/fim/completions", "https://codestral.mistral.ai/v1/fim/completions"])
  })
})
