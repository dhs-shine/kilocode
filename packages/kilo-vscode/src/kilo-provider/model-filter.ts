import type { ProviderInfo } from "../kilo-provider-utils"

export function filterPromptTrainingModels(all: ProviderInfo[], hide: boolean): ProviderInfo[] {
  if (!hide) return all
  return all.map((provider) => {
    if (provider.id !== "kilo") return provider
    const models = Object.fromEntries(
      Object.entries(provider.models).filter(([, model]) => model.mayTrainOnYourPrompts !== true),
    )
    return { ...provider, models }
  })
}
