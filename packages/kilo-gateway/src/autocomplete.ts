export type AutocompleteProviderID = "kilo" | "mistral" | "inception"

export interface AutocompleteModelDef {
  /** Stable combined value for internal comparisons. */
  readonly id: string
  /** Model value stored in settings and sent to the FIM API. */
  readonly modelID: string
  /** Human-readable label shown in settings. */
  readonly label: string
  /** Provider value stored in settings and used by the selector group. */
  readonly providerID: AutocompleteProviderID
  /** Provider display name for status bar / telemetry. */
  readonly provider: string
  /** Full model ID sent upstream by the FIM route. */
  readonly requestModel: string
  /** Provider key to use for direct BYOK FIM. Empty means Kilo Gateway. */
  readonly directProvider?: "mistral" | "inception"
  /** FIM request temperature. */
  readonly temperature: number
}

const models: AutocompleteModelDef[] = [
  {
    id: "kilo/mistralai/codestral-2508",
    modelID: "mistralai/codestral-2508",
    label: "Codestral",
    providerID: "kilo",
    provider: "Kilo Gateway",
    requestModel: "mistralai/codestral-2508",
    temperature: 0.2,
  },
  {
    id: "kilo/inception/mercury-edit-2",
    modelID: "inception/mercury-edit-2",
    label: "Mercury Edit 2",
    providerID: "kilo",
    provider: "Kilo Gateway",
    requestModel: "inception/mercury-edit-2",
    temperature: 0,
  },
  {
    id: "mistral/codestral-2508",
    modelID: "codestral-2508",
    label: "Codestral",
    providerID: "mistral",
    provider: "Mistral",
    requestModel: "codestral-2508",
    directProvider: "mistral",
    temperature: 0.2,
  },
  {
    id: "inception/mercury-edit-2",
    modelID: "mercury-edit-2",
    label: "Mercury Edit 2",
    providerID: "inception",
    provider: "Inception",
    requestModel: "mercury-edit-2",
    directProvider: "inception",
    temperature: 0,
  },
]

export const AUTOCOMPLETE_MODELS: readonly AutocompleteModelDef[] = models

export const DEFAULT_AUTOCOMPLETE_MODEL: AutocompleteModelDef = models[0]!

const aliases: Record<string, string> = {
  "kilo/mistralai/codestral-2508": "mistralai/codestral-2508",
  "kilo/inception/mercury-edit-2": "inception/mercury-edit-2",
  "inception/mercury-edit": "inception/mercury-edit-2",
}

export function getAutocompleteModel(provider?: string, model?: string): AutocompleteModelDef {
  if (model === undefined) {
    const id = provider ?? ""
    for (const m of models) {
      if (m.id === id) return m
    }
    const mid = aliases[id] ?? id
    for (const m of models) {
      if (m.providerID === "kilo" && m.modelID === mid) return m
    }
    return DEFAULT_AUTOCOMPLETE_MODEL
  }

  if (!provider) {
    const direct = models.find((m) => m.directProvider && m.modelID === model)
    if (direct) return direct
  }

  const pid = provider || "kilo"
  const mid = aliases[model ?? ""] ?? model
  for (const m of models) {
    if (m.providerID === pid && m.modelID === mid) return m
  }
  return DEFAULT_AUTOCOMPLETE_MODEL
}

export function validAutocompleteProvider(value: unknown) {
  if (typeof value !== "string") return false
  return models.some((m) => m.providerID === value)
}

export function validAutocompleteModel(value: unknown) {
  if (typeof value !== "string") return false
  const resolved = aliases[value] ?? value
  return models.some((m) => m.modelID === resolved)
}
