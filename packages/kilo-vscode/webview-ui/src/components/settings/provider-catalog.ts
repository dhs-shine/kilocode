import { iconNames, type IconName } from "@opencode-ai/ui/icons/provider"
import type { Provider } from "../../types/messages"
import {
  KILO_PROVIDER_ID,
  PROVIDER_PRIORITY as POPULAR_PROVIDER_IDS,
  createKiloFallbackProvider,
  providerOrderIndex,
} from "../../../../src/shared/provider-model"

export const CUSTOM_PROVIDER_ID = "_custom"
export { POPULAR_PROVIDER_IDS }

const POPULAR_PROVIDER_SET = new Set<string>(POPULAR_PROVIDER_IDS)

export function isPopularProvider(providerID: string) {
  return POPULAR_PROVIDER_SET.has(providerID)
}

export function popularProviderIndex(providerID: string) {
  return providerOrderIndex(providerID, POPULAR_PROVIDER_IDS)
}

function validIcon(id: string | undefined): IconName | undefined {
  if (!id) return undefined
  if (iconNames.includes(id as IconName)) return id as IconName
  return undefined
}

export function providerIcon(provider: Provider | string): IconName {
  const providerID = typeof provider === "string" ? provider : provider.id
  const icon = typeof provider === "string" ? undefined : validIcon(provider.metadata?.icon)
  if (icon) return icon
  if (providerID === KILO_PROVIDER_ID) return validIcon("kilo") ?? "synthetic"
  const fallback = validIcon(providerID)
  if (fallback) return fallback
  return "synthetic"
}

export function kiloFallbackProvider(): Provider {
  return createKiloFallbackProvider()
}

export function providerNoteKey(provider: Provider | string) {
  if (typeof provider !== "string" && provider.metadata?.noteKey) return provider.metadata.noteKey
  const providerID = typeof provider === "string" ? provider : provider.id
  if (providerID === "kilo") return "settings.providers.note.kilo"
  if (providerID === "opencode") return "settings.providers.note.opencode"
  if (providerID === "anthropic") return "settings.providers.note.anthropic"
  if (providerID === "deepseek") return "settings.providers.note.deepseek"
  if (providerID.startsWith("github-copilot")) return "settings.providers.note.copilot"
  if (providerID === "openai") return "settings.providers.note.openai"
  if (providerID === "google") return "settings.providers.note.google"
  if (providerID === "openrouter") return "settings.providers.note.openrouter"
  if (providerID === "vercel") return "settings.providers.note.vercel"
  return undefined
}

export function providerNote(provider: Provider) {
  return provider.metadata?.note
}

export function sortProviders(items: Provider[]) {
  return items.slice().sort((a, b) => {
    const rank = popularProviderIndex(a.id) - popularProviderIndex(b.id)
    if (rank !== 0) return rank
    return a.name.localeCompare(b.name)
  })
}
