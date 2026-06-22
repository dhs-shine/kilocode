import type { ProviderMetadata } from "@opencode-ai/llm"
import { ModelID, type ProviderID } from "@/provider/schema"

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
}
