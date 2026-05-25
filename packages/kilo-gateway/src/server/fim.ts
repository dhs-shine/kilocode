import { HEADER_FEATURE, KILO_API_BASE } from "../api/constants.js"
import { buildKiloHeaders } from "../headers.js"

type Auth = any

type FimProvider = "kilo" | "mistral" | "inception"

interface FimTarget {
  provider: FimProvider
  model: string
  urls: string[]
}

const FIM_TIMEOUT_MS = 30_000
const KILO_FIM_URL = KILO_API_BASE + "/api/fim/completions"
const MISTRAL_FIM_URL = "https://api.mistral.ai/v1/fim/completions"
const CODESTRAL_FIM_URL = "https://codestral.mistral.ai/v1/fim/completions"
const INCEPTION_FIM_URL = "https://api.inceptionlabs.ai/v1/fim/completions"

export function resolveFimTarget(model?: string): FimTarget {
  if (model === "mistral/codestral-2508") {
    return { provider: "mistral", model: "codestral-2508", urls: [MISTRAL_FIM_URL, CODESTRAL_FIM_URL] }
  }
  if (model === "inception-direct/mercury-edit-2") {
    return { provider: "inception", model: "mercury-edit-2", urls: [INCEPTION_FIM_URL] }
  }
  return { provider: "kilo", model: model ?? "mistralai/codestral-2501", urls: [KILO_FIM_URL] }
}

async function getProxyAuth(Auth: Auth) {
  const auth = await Auth.get("kilo")
  const token = auth?.type === "api" ? auth.key : auth?.type === "oauth" ? auth.access : undefined
  return {
    auth,
    token,
    organizationId: auth?.type === "oauth" ? auth.accountId : undefined,
  }
}

async function getProviderKey(Auth: Auth, provider: FimProvider) {
  const auth = await Auth.get(provider)
  return auth?.type === "api" ? auth.key : undefined
}

async function fetchFim(
  target: FimTarget,
  url: string,
  fallbacks: string[],
  key: string,
  input: {
    prefix: string
    suffix: string
    maxTokens: number
    temperature: number
    signal: AbortSignal
    organizationId?: string
  },
): Promise<Response> {
  console.info(`[FIM] request provider=${target.provider} model=${target.model} url=${url}`)
  const response = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${key}`,
      ...(target.provider === "kilo" ? buildKiloHeaders(undefined, { kilocodeOrganizationId: input.organizationId }) : {}),
      ...(target.provider === "kilo" ? { [HEADER_FEATURE]: "autocomplete" } : {}),
    },
    signal: input.signal,
    body: JSON.stringify({
      model: target.model,
      prompt: input.prefix,
      suffix: input.suffix,
      max_tokens: input.maxTokens,
      temperature: input.temperature,
      stream: true,
    }),
  })

  const [next] = fallbacks
  if (response.status === 401 && next) return fetchFim(target, next, fallbacks.slice(1), key, input)
  return response
}

export function createFimHandler(Auth: Auth) {
  return async (c: any) => {
    const { prefix, suffix, model, maxTokens, temperature } = c.req.valid("json")
    const target = resolveFimTarget(model)
    const fimMaxTokens = maxTokens ?? 256
    const fimTemperature = temperature ?? 0.2
    const proxy = target.provider === "kilo" ? await getProxyAuth(Auth) : undefined
    const token = target.provider === "kilo" ? proxy?.token : await getProviderKey(Auth, target.provider)

    if (target.provider === "kilo" && !proxy?.auth) {
      return c.json({ error: "Not authenticated with Kilo Gateway" }, 401)
    }

    if (target.provider === "kilo" && !token) {
      return c.json({ error: "No valid token found" }, 401)
    }

    if (!token) {
      return c.json({ error: `Missing ${target.provider} provider API key` }, 401)
    }

    const signal = AbortSignal.any([c.req.raw.signal, AbortSignal.timeout(FIM_TIMEOUT_MS)])

    let response: Response
    try {
      const [url] = target.urls
      if (!url) return c.json({ error: "No FIM endpoint configured" }, 500 as any)
      response = await fetchFim(target, url, target.urls.slice(1), token, {
        prefix,
        suffix,
        maxTokens: fimMaxTokens,
        temperature: fimTemperature,
        signal,
        organizationId: proxy?.organizationId,
      })
    } catch (err) {
      if (err instanceof DOMException && err.name === "TimeoutError") {
        return c.json({ error: "FIM request timed out" }, 504 as any)
      }
      if (signal.aborted) return c.json({ error: "FIM request canceled" }, 499 as any)
      throw err
    }

    if (!response.ok) {
      const text = await response.text()
      return c.json({ error: `FIM request failed: ${response.status} ${text}` }, response.status as any)
    }

    return new Response(response.body, {
      headers: {
        "Content-Type": "text/event-stream",
        "Cache-Control": "no-cache",
        Connection: "keep-alive",
      },
    })
  }
}
