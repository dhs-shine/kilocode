import type { ModelSelection, Provider } from "../types/messages"
import { KILO_PROVIDER_ID } from "../../../src/shared/provider-model"
import { isModelValid } from "./provider-utils"

function validate(
  providers: Record<string, Provider>,
  connected: string[],
  selection: ModelSelection | null | undefined,
): ModelSelection | null {
  if (!selection) return null
  if (Object.keys(providers).length === 0) return selection
  return isModelValid(providers, connected, selection) ? selection : null
}

function recent(
  providers: Record<string, Provider>,
  connected: string[],
  selections: ModelSelection[] | undefined,
): ModelSelection | null {
  for (const item of selections ?? []) {
    const selection = validate(providers, connected, item)
    if (selection) return selection
  }
  return null
}

export function resolveModelSelection(input: {
  providers: Record<string, Provider>
  connected: string[]
  override?: ModelSelection | null
  mode?: ModelSelection | null
  global?: ModelSelection | null
  recent?: ModelSelection[]
  fallback?: ModelSelection | null
}): ModelSelection | null {
  return (
    validate(input.providers, input.connected, input.override) ??
    validate(input.providers, input.connected, input.mode) ??
    validate(input.providers, input.connected, input.global) ??
    recent(input.providers, input.connected, input.recent) ??
    input.fallback ??
    null
  )
}

export function kiloCatalogModelStatus(input: {
  agents: boolean
  loaded: boolean
  providers: Record<string, Provider>
  modelID: string
}): "pending" | "invalid" | "apply" {
  if (!input.agents || !input.loaded) return "pending"
  return input.providers[KILO_PROVIDER_ID]?.models[input.modelID] ? "apply" : "invalid"
}

export function isCurrentModelSelections(revision: number | undefined, current: number): boolean {
  return revision === undefined || revision === current
}
