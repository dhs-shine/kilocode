import { describe, expect, it } from "bun:test"
import { kiloModelFromURI } from "../../src/kilo-provider/model-uri"

describe("kiloModelFromURI", () => {
  it("accepts arbitrary Kilo catalog model ids", () => {
    expect(kiloModelFromURI({ path: "/kilocode/model", query: "model=vendor%2Fnew-live-model" })).toBe(
      "vendor/new-live-model",
    )
  })

  it("preserves additional slashes inside model ids", () => {
    expect(kiloModelFromURI({ path: "/kilocode/model", query: "model=vendor%2Ffamily%2Fmodel" })).toBe(
      "vendor/family/model",
    )
  })

  it("rejects missing and empty model ids", () => {
    expect(kiloModelFromURI({ path: "/kilocode/model", query: "" })).toBeUndefined()
    expect(kiloModelFromURI({ path: "/kilocode/model", query: "model=" })).toBeUndefined()
  })

  it("rejects unrelated URI paths", () => {
    expect(kiloModelFromURI({ path: "/kilocode/s/session-id", query: "model=vendor%2Fmodel" })).toBeUndefined()
  })
})
