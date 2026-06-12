import { describe, expect, it } from "bun:test"
import type { ProviderInfo } from "../../src/kilo-provider-utils"
import { filterPromptTrainingModels } from "../../src/kilo-provider/model-filter"

const providers = [
  {
    id: "kilo",
    name: "Kilo Gateway",
    source: "api",
    env: [],
    models: {
      training: { id: "training", name: "Training", mayTrainOnYourPrompts: true },
      private: { id: "private", name: "Private", mayTrainOnYourPrompts: false },
      unknown: { id: "unknown", name: "Unknown" },
    },
  },
  {
    id: "other",
    name: "Other",
    source: "api",
    env: [],
    models: {
      training: { id: "training", name: "Training", mayTrainOnYourPrompts: true },
    },
  },
] as ProviderInfo[]

describe("filterPromptTrainingModels", () => {
  it("preserves the catalog when the filter is disabled", () => {
    expect(filterPromptTrainingModels(providers, false)).toBe(providers)
  })

  it("hides only explicitly marked Kilo Gateway models", () => {
    const result = filterPromptTrainingModels(providers, true)

    expect(Object.keys(result[0]!.models)).toEqual(["private", "unknown"])
    expect(Object.keys(result[1]!.models)).toEqual(["training"])
    expect(Object.keys(providers[0]!.models)).toEqual(["training", "private", "unknown"])
  })
})
