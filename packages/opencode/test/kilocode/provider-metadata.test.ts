import { describe, expect, test } from "bun:test"
import { providerMetadata } from "../../src/kilocode/provider/metadata"

describe("providerMetadata", () => {
  test("returns shared provider note and icon metadata", () => {
    expect(providerMetadata("openai")).toEqual({
      noteKey: "settings.providers.note.openai",
      note: "GPT and Codex models with API key or ChatGPT login",
      icon: "openai",
    })
  })

  test("maps github copilot aliases to stable metadata", () => {
    expect(providerMetadata("github-copilot-custom")).toEqual({
      noteKey: "settings.providers.note.copilot",
      note: "Claude models for coding assistance",
      icon: "github-copilot",
    })
  })

  test("uses the Kilo icon for Kilo Gateway", () => {
    expect(providerMetadata("kilo")).toEqual({
      noteKey: "settings.providers.note.kilo",
      note: "Access 500+ AI models",
      icon: "kilo",
    })
  })

  test("falls back to synthetic icon for unknown providers", () => {
    expect(providerMetadata("unknown-provider")).toEqual({ icon: "synthetic" })
  })
})
