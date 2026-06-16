import { describe, expect, it } from "bun:test"
import { parseSwitchLink } from "../../src/kilo-provider/switch-link"

describe("parseSwitchLink", () => {
  it("parses the switch route and decodes the model ID", () => {
    expect(parseSwitchLink("/kilocode/switch", "model=kilo-auto%2Ffree")).toEqual({
      modelID: "kilo-auto/free",
    })
  })

  it("includes an optional agent", () => {
    expect(parseSwitchLink("/kilocode/switch", "model=kilo-auto%2Ffree&agent=plan")).toEqual({
      modelID: "kilo-auto/free",
      agent: "plan",
    })
  })

  it("accepts mode as an agent alias", () => {
    expect(parseSwitchLink("/kilocode/switch", "model=kilo-auto%2Ffree&mode=code")).toEqual({
      modelID: "kilo-auto/free",
      agent: "code",
    })
  })

  it("prefers agent when both parameter names are present", () => {
    expect(parseSwitchLink("/kilocode/switch", "model=kilo-auto%2Ffree&agent=plan&mode=code")).toEqual({
      modelID: "kilo-auto/free",
      agent: "plan",
    })
  })

  it("keeps the previous model route compatible", () => {
    expect(parseSwitchLink("/kilocode/model", "model=kilo-auto%2Ffree")).toEqual({
      modelID: "kilo-auto/free",
    })
  })

  it("rejects unsupported routes and missing models", () => {
    expect(parseSwitchLink("/kilocode/other", "model=kilo-auto%2Ffree")).toBeUndefined()
    expect(parseSwitchLink("/kilocode/switch", "agent=plan")).toBeUndefined()
  })
})
