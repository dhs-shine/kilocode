import { describe, expect, it } from "bun:test"
import { affectsTerminalFont, resolveTerminalFont } from "../../src/agent-manager/terminal-font"

describe("Agent Manager terminal font", () => {
  it("resolves terminal settings without inheriting the editor size", () => {
    expect(resolveTerminalFont(undefined, undefined, undefined)).toEqual({
      fontFamily: "Menlo, Monaco, 'Courier New', monospace",
      fontSize: process.platform === "darwin" ? 12 : 14,
    })
    expect(resolveTerminalFont("MesloLGS NF", 16, "Menlo")).toEqual({
      fontFamily: "MesloLGS NF",
      fontSize: 16,
    })
    expect(resolveTerminalFont(undefined, 16, "Menlo")).toEqual({
      fontFamily: "Menlo",
      fontSize: 16,
    })
  })

  it("isolates terminal settings from Kilo webview font size changes", () => {
    const event = (key: string) =>
      ({
        affectsConfiguration: (target: string) => target === key,
      }) as Parameters<typeof affectsTerminalFont>[0]

    expect(affectsTerminalFont(event("terminal.integrated.fontFamily"))).toBe(true)
    expect(affectsTerminalFont(event("terminal.integrated.fontSize"))).toBe(true)
    expect(affectsTerminalFont(event("editor.fontFamily"))).toBe(true)
    expect(affectsTerminalFont(event("editor.fontSize"))).toBe(false)
    expect(affectsTerminalFont(event("terminal.integrated.letterSpacing"))).toBe(false)
  })
})
