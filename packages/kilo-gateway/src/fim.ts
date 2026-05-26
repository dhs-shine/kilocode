import { KILO_API_BASE } from "./api/constants.js"
import { getAutocompleteModel, type AutocompleteProviderID, type DirectAutocompleteProviderID } from "./autocomplete.js"
import { CODESTRAL_FIM_URL, MISTRAL_FIM_URL } from "./mistral-fim-endpoint.js"

export { requestMistralFim } from "./mistral-fim-endpoint.js"

export const DIRECT_FIM_ENV: Record<DirectAutocompleteProviderID, string[]> = {
  mistral: ["MISTRAL_API_KEY"],
  inception: ["INCEPTION_API_KEY"],
}

export interface FimTarget {
  provider: AutocompleteProviderID
  model: string
  urls: string[]
}

const KILO_FIM_URL = KILO_API_BASE + "/api/fim/completions"
const INCEPTION_FIM_URL = "https://api.inceptionlabs.ai/v1/fim/completions"

export function resolveFimTarget(provider?: string, model?: string): FimTarget {
  if (!provider || provider === "kilo") {
    return { provider: "kilo", model: model ?? "mistralai/codestral-2501", urls: [KILO_FIM_URL] }
  }

  const info = getAutocompleteModel(provider, model)
  if (info.directProvider === "mistral") {
    return { provider: "mistral", model: info.requestModel, urls: [MISTRAL_FIM_URL, CODESTRAL_FIM_URL] }
  }
  if (info.directProvider === "inception") {
    return { provider: "inception", model: info.requestModel, urls: [INCEPTION_FIM_URL] }
  }
  return { provider: "kilo", model: model ?? "mistralai/codestral-2501", urls: [KILO_FIM_URL] }
}
