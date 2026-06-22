import type { ProviderMetadata } from "@opencode-ai/llm"
import { ModelID, ProviderID } from "@/provider/schema"

export namespace KiloRoutedModel {
  const ns = "kilocode"
  const key = "routedModelID"

  export function write(meta: ProviderMetadata | undefined, modelID: string | undefined) {
    const id = modelID?.trim()
    if (!id) return meta
    return {
      ...(meta ?? {}),
      [ns]: {
        ...(meta?.[ns] ?? {}),
        [key]: id,
      },
    } satisfies ProviderMetadata
  }

  export function read(meta: ProviderMetadata | undefined, providerID: ProviderID) {
    const value = meta?.[ns]?.[key]
    if (typeof value !== "string") return undefined
    const id = value.trim()
    if (!id) return undefined
    return {
      providerID,
      modelID: ModelID.make(id),
    }
  }

  export function readAuto(
    meta: ProviderMetadata | undefined,
    input: { providerID: ProviderID; modelID: string; selected?: string },
  ) {
    if (input.providerID !== ProviderID.kilo) return undefined
    if (!input.modelID.startsWith("kilo-auto/")) return undefined
    const model = read(meta, input.providerID)
    if (!model) return undefined
    if (model.modelID === input.modelID || model.modelID === input.selected) return undefined
    return model
  }
}
