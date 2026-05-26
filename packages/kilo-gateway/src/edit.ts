import { getAutocompleteModel, type DirectAutocompleteProviderID } from "./autocomplete.js"

/**
 * Env var(s) consulted as a fallback for BYOK keys when the provider hasn't
 * been authenticated via the gateway's Auth store. Mirrors `DIRECT_FIM_ENV`.
 */
export const DIRECT_EDIT_ENV: Record<DirectAutocompleteProviderID, string[]> = {
  mistral: ["MISTRAL_API_KEY"],
  inception: ["INCEPTION_API_KEY"],
}

export type EditTarget =
  | { provider: "inception"; model: string; url: string }
  | { provider: "kilo"; model: string; url: string }

const INCEPTION_EDIT_URL = "https://api.inceptionlabs.ai/v1/edit/completions"

/**
 * Pick the upstream edit endpoint for a (provider, model) pair. Only Inception
 * is wired up today — Mercury is the only model family with a documented
 * /v1/edit/completions endpoint. Mistral does not expose a comparable surface.
 */
export function resolveEditTarget(provider?: string, model?: string): EditTarget {
  const info = getAutocompleteModel(provider, model)
  if (info.directProvider === "inception") {
    return { provider: "inception", model: info.requestModel, url: INCEPTION_EDIT_URL }
  }
  // Kilo Gateway does not currently proxy an edit endpoint; callers should
  // fall back to FIM. We still return a kilo target so the handler can surface
  // a 400 rather than silently routing somewhere unexpected.
  return { provider: "kilo", model: info.requestModel, url: "" }
}
