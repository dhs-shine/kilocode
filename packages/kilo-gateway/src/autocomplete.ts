export interface AutocompleteModelDef {
  /** Stable setting value. */
  readonly id: string
  /** Model ID displayed under the selector provider group. */
  readonly modelID: string
  /** Human-readable label shown in settings. */
  readonly label: string
  /** Provider ID used by the selector group. */
  readonly providerID: string
  /** Provider display name for status bar / telemetry. */
  readonly provider: string
  /** Full model ID sent to the FIM API. */
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
    id: "inception/provider/mercury-edit-2",
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
  "mistralai/codestral-2508": "kilo/mistralai/codestral-2508",
  "inception/mercury-edit": "kilo/inception/mercury-edit-2",
  "inception/mercury-edit-2": "kilo/inception/mercury-edit-2",
}

export function getAutocompleteModel(id: string): AutocompleteModelDef {
  const resolved = aliases[id] ?? id
  for (const m of models) {
    if (m.id === resolved) return m
  }
  return DEFAULT_AUTOCOMPLETE_MODEL
}
