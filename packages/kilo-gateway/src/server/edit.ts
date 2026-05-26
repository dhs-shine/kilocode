import { DIRECT_EDIT_ENV, resolveEditTarget, type EditTarget } from "../edit.js"
import type { AuthStore } from "./handlers.js"

type Auth = Pick<AuthStore, "get">

const EDIT_TIMEOUT_MS = 30_000
const MAX_TOKENS_DEFAULT = 512

async function getProviderKey(Auth: Auth, provider: "inception" | "mistral"): Promise<string | undefined> {
  const auth = await Auth.get(provider)
  if (auth?.type === "api") return auth.key
  return DIRECT_EDIT_ENV[provider].map((key) => process.env[key]).find(Boolean)
}

/**
 * Extract the rewritten code from Mercury's reply. Mercury always wraps the
 * editable region in a triple-backtick fence, sometimes with a language tag
 * and sometimes with `<|code_to_edit|>` markers inside. Mirrors the parser the
 * VSCode side used to run; doing it gateway-side keeps the Mercury contract
 * in one place.
 */
function extractFencedBody(message: string): string {
  if (!message) return ""
  const fenceOpen = message.indexOf("```")
  if (fenceOpen === -1) return message
  const afterFenceOpen = message.indexOf("\n", fenceOpen + 3)
  if (afterFenceOpen === -1) return ""
  const fenceClose = message.lastIndexOf("```")
  if (fenceClose <= afterFenceOpen) return ""
  let body = message.slice(afterFenceOpen + 1, fenceClose)
  if (body.endsWith("\n")) body = body.slice(0, -1)
  body = body.replace(/^<\|code_to_edit\|>\n?/, "")
  body = body.replace(/\n?<\|\/code_to_edit\|>$/, "")
  return body
}

interface UpstreamResponse {
  choices?: Array<{ message?: { content?: string } }>
  usage?: { prompt_tokens?: number; completion_tokens?: number }
}

export function createEditHandler(Auth: Auth) {
  return async (c: any) => {
    const { content, provider, model, maxTokens } = c.req.valid("json")
    const target = resolveEditTarget(provider, model)

    if (target.provider !== "inception") {
      return c.json({ error: "Next Edit currently requires the Inception provider (mercury-edit-2)." }, 400 as any)
    }

    const token = await getProviderKey(Auth, target.provider)
    if (!token) {
      return c.json({ error: `Missing ${target.provider} provider API key` }, 401 as any)
    }

    const signal = AbortSignal.any([c.req.raw.signal, AbortSignal.timeout(EDIT_TIMEOUT_MS)])
    console.info(`[EDIT] request provider=${target.provider} model=${target.model} url=${target.url} chars=${content.length}`)

    let response: Response
    try {
      response = await fetch(target.url, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        signal,
        body: JSON.stringify({
          model: target.model,
          max_tokens: maxTokens ?? MAX_TOKENS_DEFAULT,
          // Mercury rejects role:"system" on this endpoint — must be a single
          // user message. See the integration's constants.ts for context.
          messages: [{ role: "user", content }],
        }),
      })
    } catch (err) {
      if (err instanceof DOMException && err.name === "TimeoutError") {
        return c.json({ error: "Edit request timed out" }, 504 as any)
      }
      if (signal.aborted) return c.json({ error: "Edit request canceled" }, 499 as any)
      throw err
    }

    if (!response.ok) {
      const text = await safeText(response)
      return c.json({ error: `Edit request failed: ${response.status} ${text}` }, response.status as any)
    }

    const json = (await response.json()) as UpstreamResponse
    const replyContent = json.choices?.[0]?.message?.content ?? ""
    const body = extractFencedBody(replyContent)
    return c.json({
      content: body,
      usage: json.usage
        ? {
            prompt_tokens: json.usage.prompt_tokens,
            completion_tokens: json.usage.completion_tokens,
          }
        : undefined,
    })
  }
}

async function safeText(res: Response): Promise<string> {
  try {
    return await res.text()
  } catch {
    return "<unreadable>"
  }
}

// Re-export the target type for tests + the opencode handler
export type { EditTarget }
