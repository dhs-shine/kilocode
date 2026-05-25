import { AUTOCOMPLETE_MODELS, getAutocompleteModel } from "../../../../src/shared/autocomplete-models"
import type { EnrichedModel } from "../../context/provider"

export function getAutocompleteSelection(id: string) {
  const model = getAutocompleteModel(id)
  return { providerID: model.providerID, modelID: model.modelID }
}

export function getAutocompleteSettingID(providerID: string, modelID: string) {
  return AUTOCOMPLETE_MODELS.find((m) => m.providerID === providerID && m.modelID === modelID)?.id
}

export const AUTOCOMPLETE_SELECTOR_MODELS: EnrichedModel[] = AUTOCOMPLETE_MODELS.map((m) => ({
  id: m.modelID,
  name: m.label,
  providerID: m.providerID,
  providerName: m.provider,
}))
