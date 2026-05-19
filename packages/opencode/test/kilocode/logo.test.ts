import { describe, expect, test } from "bun:test"
import { plain, supports, tui } from "../../src/kilocode/cli/logo"

describe("kilocode logo", () => {
  test("falls back on Windows terminals", () => {
    expect(supports({}, "win32")).toBe(false)
    expect(supports({ WT_SESSION: "session" }, "linux")).toBe(false)
  })

  test("allows an override", () => {
    expect(supports({ KILO_UNICODE_LOGO: "1", WT_SESSION: "session" }, "linux")).toBe(true)
    expect(supports({ KILO_UNICODE_LOGO: "0" }, "linux")).toBe(false)
  })

  test("uses modern and fallback logo variants", () => {
    expect(tui({ KILO_UNICODE_LOGO: "1" }, "linux").join("\n")).toContain("🬺🬏")
    expect(tui({ WT_SESSION: "session" }, "linux").join("\n")).not.toContain("🬺🬏")
    expect(plain({ WT_SESSION: "session" }, "linux").join("\n")).not.toContain("🬁🬬")
  })
})
