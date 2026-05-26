import { describe, expect, test } from "bun:test"
import { resolveFimTarget } from "../src/server/fim"

describe("FIM target resolution", () => {
  test("keeps gateway autocomplete models on Kilo Gateway", () => {
    expect(resolveFimTarget("kilo", "mistralai/codestral-2508")).toEqual({
      provider: "kilo",
      model: "mistralai/codestral-2508",
      urls: ["https://api.kilo.ai/api/fim/completions"],
    })
    expect(resolveFimTarget("kilo", "inception/mercury-edit-2")).toEqual({
      provider: "kilo",
      model: "inception/mercury-edit-2",
      urls: ["https://api.kilo.ai/api/fim/completions"],
    })
  })

  test("routes explicit provider autocomplete models directly", () => {
    expect(resolveFimTarget("mistral", "codestral-2508")).toEqual({
      provider: "mistral",
      model: "codestral-2508",
      urls: ["https://api.mistral.ai/v1/fim/completions", "https://codestral.mistral.ai/v1/fim/completions"],
    })
    expect(resolveFimTarget("inception", "mercury-edit-2")).toEqual({
      provider: "inception",
      model: "mercury-edit-2",
      urls: ["https://api.inceptionlabs.ai/v1/fim/completions"],
    })
  })

  test("maps legacy gateway IDs to explicit Kilo Gateway targets", () => {
    expect(resolveFimTarget(undefined, "mistralai/codestral-2508")).toEqual({
      provider: "kilo",
      model: "mistralai/codestral-2508",
      urls: ["https://api.kilo.ai/api/fim/completions"],
    })
    expect(resolveFimTarget(undefined, "inception/mercury-edit")).toEqual({
      provider: "kilo",
      model: "inception/mercury-edit-2",
      urls: ["https://api.kilo.ai/api/fim/completions"],
    })
    expect(resolveFimTarget(undefined, "inception/mercury-edit-2")).toEqual({
      provider: "kilo",
      model: "inception/mercury-edit-2",
      urls: ["https://api.kilo.ai/api/fim/completions"],
    })
  })
})
