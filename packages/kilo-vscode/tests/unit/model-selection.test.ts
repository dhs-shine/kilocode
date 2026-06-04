import { describe, expect, it } from "bun:test"
import {
  isCurrentModelSelections,
  kiloCatalogModelStatus,
  resolveModelSelection,
} from "../../webview-ui/src/context/model-selection"
import { KILO_AUTO, parseModelString } from "../../src/shared/provider-model"
import type { Provider } from "../../webview-ui/src/types/messages"

function makeProvider(id: string, name: string, modelIds: string[]): Provider {
  const models: Provider["models"] = {}
  for (const modelID of modelIds) {
    models[modelID] = { id: modelID, name: modelID }
  }
  return { id, name, models }
}

const providers = {
  kilo: makeProvider("kilo", "Kilo Gateway", ["kilo-auto/free", "vendor/new-live-model"]),
  anthropic: makeProvider("anthropic", "Anthropic", ["claude-sonnet-4"]),
  openai: makeProvider("openai", "OpenAI", ["gpt-4.1"]),
}

describe("parseModelString", () => {
  it("parses provider/model pairs", () => {
    expect(parseModelString("anthropic/claude-sonnet-4")).toEqual({
      providerID: "anthropic",
      modelID: "claude-sonnet-4",
    })
  })

  it("keeps slashes inside kilo model ids", () => {
    expect(parseModelString("kilo/kilo-auto/free")).toEqual({
      providerID: "kilo",
      modelID: "kilo-auto/free",
    })
  })

  it("returns null for invalid values", () => {
    expect(parseModelString(undefined)).toBeNull()
    expect(parseModelString("claude-sonnet-4")).toBeNull()
  })
})

describe("Kilo catalog model URI validation", () => {
  it("waits for agents and provider metadata", () => {
    expect(kiloCatalogModelStatus({ agents: false, loaded: true, providers, modelID: "vendor/new-live-model" })).toBe(
      "pending",
    )
    expect(kiloCatalogModelStatus({ agents: true, loaded: false, providers, modelID: "vendor/new-live-model" })).toBe(
      "pending",
    )
  })

  it("rejects models missing from the loaded Kilo catalog", () => {
    expect(kiloCatalogModelStatus({ agents: true, loaded: true, providers, modelID: "vendor/missing-model" })).toBe(
      "invalid",
    )
  })

  it("applies arbitrary models exposed by the loaded Kilo catalog", () => {
    expect(kiloCatalogModelStatus({ agents: true, loaded: true, providers, modelID: "vendor/new-live-model" })).toBe(
      "apply",
    )
  })

  it("does not accept models exposed only by non-Kilo providers", () => {
    expect(kiloCatalogModelStatus({ agents: true, loaded: true, providers, modelID: "gpt-4.1" })).toBe("invalid")
  })
})

describe("isCurrentModelSelections", () => {
  it("rejects stale hydration responses after local selection changes", () => {
    expect(isCurrentModelSelections(0, 1)).toBe(false)
    expect(isCurrentModelSelections(1, 1)).toBe(true)
    expect(isCurrentModelSelections(undefined, 1)).toBe(true)
  })
})

describe("resolveModelSelection", () => {
  it("prefers a valid override", () => {
    const result = resolveModelSelection({
      providers,
      connected: ["anthropic", "openai"],
      override: { providerID: "openai", modelID: "gpt-4.1" },
      mode: { providerID: "anthropic", modelID: "claude-sonnet-4" },
      fallback: KILO_AUTO,
    })
    expect(result).toEqual({ providerID: "openai", modelID: "gpt-4.1" })
  })

  it("falls back from an invalid override to the mode model", () => {
    const result = resolveModelSelection({
      providers,
      connected: ["anthropic"],
      override: { providerID: "openai", modelID: "gpt-4.1" },
      mode: { providerID: "anthropic", modelID: "claude-sonnet-4" },
      fallback: KILO_AUTO,
    })
    expect(result).toEqual({ providerID: "anthropic", modelID: "claude-sonnet-4" })
  })

  it("falls back from invalid config to the first valid recent model", () => {
    const result = resolveModelSelection({
      providers,
      connected: ["openai"],
      mode: { providerID: "anthropic", modelID: "claude-sonnet-4" },
      recent: [
        { providerID: "anthropic", modelID: "claude-sonnet-4" },
        { providerID: "openai", modelID: "gpt-4.1" },
      ],
      fallback: KILO_AUTO,
    })
    expect(result).toEqual({ providerID: "openai", modelID: "gpt-4.1" })
  })

  it("uses kilo auto as the explicit final fallback", () => {
    const result = resolveModelSelection({
      providers,
      connected: [],
      fallback: KILO_AUTO,
    })
    expect(result).toEqual(KILO_AUTO)
  })

  it("keeps the explicit fallback even when kilo is missing from the loaded catalog", () => {
    const result = resolveModelSelection({
      providers: { openai: providers.openai },
      connected: [],
      fallback: KILO_AUTO,
    })
    expect(result).toEqual(KILO_AUTO)
  })

  it("keeps the raw preference order before providers load", () => {
    const result = resolveModelSelection({
      providers: {},
      connected: [],
      override: { providerID: "openai", modelID: "gpt-4.1" },
      mode: { providerID: "anthropic", modelID: "claude-sonnet-4" },
      fallback: KILO_AUTO,
    })
    expect(result).toEqual({ providerID: "openai", modelID: "gpt-4.1" })
  })
})
