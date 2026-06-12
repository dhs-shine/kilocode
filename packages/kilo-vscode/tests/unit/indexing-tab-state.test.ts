import { describe, expect, it } from "bun:test"
import {
  indexingConfig,
  indexingDescription,
  indexingEnabled,
  indexingEnabledInherited,
  indexingInheritance,
  indexingSource,
  indexingUpdate,
} from "../../webview-ui/src/components/settings/indexing-tab-state"

describe("indexing tab scope state", () => {
  it("uses inherited and explicit project enablement", () => {
    expect(indexingEnabled("project", { enabled: true }, {})).toBe(true)
    expect(indexingEnabledInherited("project", { enabled: false }, {})).toBe(true)
    expect(indexingEnabled("project", { enabled: true }, { enabled: false })).toBe(false)
    expect(indexingEnabledInherited("project", { enabled: true }, { enabled: false })).toBe(false)
  })

  it("preserves explicit null overrides and recursively inherits undefined leaves", () => {
    expect(
      indexingConfig(
        "project",
        { model: "global-model", dimension: 1024, qdrant: { url: "http://global", apiKey: "secret" } },
        { model: null, dimension: null, qdrant: { url: "http://project", apiKey: undefined } },
      ),
    ).toEqual({ model: null, dimension: null, qdrant: { url: "http://project", apiKey: "secret" } })
  })

  it("keeps inherited values out of project updates", () => {
    expect(
      indexingUpdate(
        "project",
        { enabled: true, provider: "openai", openai: { apiKey: "global" } },
        { qdrant: { url: "http://project" } },
        { enabled: false },
      ),
    ).toEqual({ enabled: false, qdrant: { url: "http://project" } })
  })

  it("classifies inherited and mixed fields", () => {
    const global = {
      provider: "openai-compatible" as const,
      "openai-compatible": { baseUrl: "https://global.test", apiKey: "secret" },
    }
    const project = { "openai-compatible": { baseUrl: "https://project.test" } }
    const paths = [
      ["openai-compatible", "baseUrl"],
      ["openai-compatible", "apiKey"],
    ]

    expect(indexingInheritance("project", global, project, [["provider"]])).toBe("inherited")
    expect(indexingSource("project", global, project, paths)).toBe("mixed")
    expect(indexingDescription("Configure this value.", "partial")).toBe(
      "Configure this value. Some values are inherited from global config.",
    )
    expect(indexingSource("project", {}, {}, [["vectorStore"]])).toBe("default")
  })
})
