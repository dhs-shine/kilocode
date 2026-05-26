import { createHash } from "node:crypto"

export const MISTRAL_FIM_URL = "https://api.mistral.ai/v1/fim/completions"
export const CODESTRAL_FIM_URL = "https://codestral.mistral.ai/v1/fim/completions"

const cache = new Map<string, string>()

function fingerprint(key: string) {
  return createHash("sha256").update(key).digest("hex").slice(0, 16)
}

export function isMistralEndpointMismatch(response: Response) {
  return response.status === 401 || response.status === 403
}

export function clearMistralFimEndpointCache() {
  cache.clear()
}

export function getCachedMistralFimEndpoint(key: string) {
  return cache.get(fingerprint(key))
}

export async function requestMistralFim(key: string, request: (url: string) => Promise<Response>) {
  const id = fingerprint(key)
  const preferred = cache.get(id) ?? MISTRAL_FIM_URL
  const alternate = preferred === MISTRAL_FIM_URL ? CODESTRAL_FIM_URL : MISTRAL_FIM_URL
  const first = await request(preferred)

  if (first.ok) return first
  if (!isMistralEndpointMismatch(first)) return first

  cache.delete(id)
  const second = await request(alternate)
  if (second.ok) cache.set(id, alternate)
  return second
}
