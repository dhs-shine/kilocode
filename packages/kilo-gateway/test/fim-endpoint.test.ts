import { describe, expect, test } from "bun:test"
import {
  CODESTRAL_FIM_URL,
  MISTRAL_FIM_URL,
  clearMistralFimEndpointCache,
  getCachedMistralFimEndpoint,
  requestMistralFim,
} from "../src/fim-endpoint"

function response(status: number) {
  return new Response(null, { status })
}

describe("Mistral FIM endpoint cache", () => {
  test("caches Codestral endpoint after successful fallback", async () => {
    clearMistralFimEndpointCache()
    const urls: string[] = []
    const first = await requestMistralFim("key-a", async (url) => {
      urls.push(url)
      return response(url === MISTRAL_FIM_URL ? 401 : 200)
    })
    const second = await requestMistralFim("key-a", async (url) => {
      urls.push(url)
      return response(200)
    })

    expect(first.ok).toBe(true)
    expect(second.ok).toBe(true)
    expect(urls).toEqual([MISTRAL_FIM_URL, CODESTRAL_FIM_URL, CODESTRAL_FIM_URL])
    expect(getCachedMistralFimEndpoint("key-a")).toBe(CODESTRAL_FIM_URL)
  })

  test("does not cache fallback for invalid credentials", async () => {
    clearMistralFimEndpointCache()
    const urls: string[] = []
    const res = await requestMistralFim("key-b", async (url) => {
      urls.push(url)
      return response(401)
    })

    expect(res.status).toBe(401)
    expect(urls).toEqual([MISTRAL_FIM_URL, CODESTRAL_FIM_URL])
    expect(getCachedMistralFimEndpoint("key-b")).toBeUndefined()
  })

  test("keeps endpoint preference scoped to credential fingerprint", async () => {
    clearMistralFimEndpointCache()
    const urls: string[] = []
    await requestMistralFim("key-c", async (url) => {
      urls.push(url)
      return response(url === MISTRAL_FIM_URL ? 403 : 200)
    })
    await requestMistralFim("key-d", async (url) => {
      urls.push(url)
      return response(200)
    })

    expect(urls).toEqual([MISTRAL_FIM_URL, CODESTRAL_FIM_URL, MISTRAL_FIM_URL])
    expect(getCachedMistralFimEndpoint("key-c")).toBe(CODESTRAL_FIM_URL)
    expect(getCachedMistralFimEndpoint("key-d")).toBeUndefined()
  })

  test("clears stale preference and probes alternate endpoint", async () => {
    clearMistralFimEndpointCache()
    const urls: string[] = []
    await requestMistralFim("key-e", async (url) => response(url === MISTRAL_FIM_URL ? 401 : 200))
    const res = await requestMistralFim("key-e", async (url) => {
      urls.push(url)
      return response(url === CODESTRAL_FIM_URL ? 401 : 200)
    })

    expect(res.ok).toBe(true)
    expect(urls).toEqual([CODESTRAL_FIM_URL, MISTRAL_FIM_URL])
    expect(getCachedMistralFimEndpoint("key-e")).toBe(MISTRAL_FIM_URL)
  })
})
