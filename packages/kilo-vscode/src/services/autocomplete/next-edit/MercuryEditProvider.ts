import type { KiloConnectionService } from "../../cli-backend"
import { nesLog, nesWarn } from "./log"
import { buildMercuryEditPrompt } from "./mercuryPromptTemplate"
import type { MercuryEditRequestContext, MercuryEditSuggestion } from "./types"

const MERCURY_MAX_TOKENS = 512
const PROVIDER_ID = "inception"
const MODEL_ID = "mercury-next-edit"

export interface MercuryEditProviderOptions {
  connectionService: KiloConnectionService
  /** AbortSignal for cancellation (cursor moves, escape, etc.). */
  signal?: AbortSignal
}

/**
 * Thin wrapper around the SDK's `client.kilo.edit(...)` SSE endpoint.
 * The gateway (in `packages/kilo-gateway/src/server/edit.ts`) handles auth,
 * routing to Mercury's `/v1/edit/completions`, and unwrapping the
 * triple-backtick fence from the model response — so the VSCode side only
 * deals in already-parsed code.
 */
export class MercuryEditProvider {
  constructor(private readonly options: MercuryEditProviderOptions) {}

  async suggest(ctx: MercuryEditRequestContext): Promise<MercuryEditSuggestion | null> {
    const userContent = buildMercuryEditPrompt(ctx)
    const start = Date.now()
    nesLog(
      `-> /kilo/edit model=${MODEL_ID} promptChars=${userContent.length} region=[${ctx.editableRegionStartLine},${ctx.editableRegionEndLine}] diffs=${ctx.editDiffHistory.length} snippets=${ctx.recentlyViewedSnippets.length}`,
    )

    const client = await this.options.connectionService.getClientAsync()
    try {
      const { data, error } = await client.kilo.edit(
        {
          content: userContent,
          provider: PROVIDER_ID,
          model: MODEL_ID,
          maxTokens: MERCURY_MAX_TOKENS,
        },
        { signal: this.options.signal, throwOnError: false },
      )
      const latencyMs = Date.now() - start
      if (error) {
        const status = typeof (error as any)?.status === "number" ? (error as any).status : null
        nesWarn(`<- error ${status ?? "?"} (${latencyMs}ms): ${safeStringify(error)}`)
        throw new MercuryEditError(`Edit request failed: ${safeStringify(error)}`, status)
      }
      return this.parseSuccess(ctx, data, latencyMs)
    } catch (err) {
      if ((err as Error)?.name === "AbortError") throw err
      if (err instanceof MercuryEditError) throw err
      const msg = err instanceof Error ? err.message : String(err)
      nesWarn(`<- transport error: ${msg}`)
      throw new MercuryEditError(`Edit request failed: ${msg}`, null)
    }
  }

  private parseSuccess(
    ctx: MercuryEditRequestContext,
    data: { content?: string; usage?: { prompt_tokens?: number; completion_tokens?: number } } | undefined,
    latencyMs: number,
  ): MercuryEditSuggestion | null {
    const replacement = data?.content ?? null
    const usage = data?.usage
    nesLog(`<- ok (${latencyMs}ms) tokens=${usage?.completion_tokens ?? "?"} parsedChars=${replacement?.length ?? 0}`)
    if (replacement === null || replacement.length === 0) return null
    return {
      replacement,
      editableRegionStartLine: ctx.editableRegionStartLine,
      editableRegionEndLine: ctx.editableRegionEndLine,
      latencyMs,
      inputTokens: usage?.prompt_tokens,
      outputTokens: usage?.completion_tokens,
    }
  }
}

export class MercuryEditError extends Error {
  constructor(message: string, public readonly status: number | null) {
    super(message)
    this.name = "MercuryEditError"
  }
}

function safeStringify(value: unknown): string {
  try {
    if (typeof value === "string") return value
    return JSON.stringify(value)
  } catch {
    return String(value)
  }
}
