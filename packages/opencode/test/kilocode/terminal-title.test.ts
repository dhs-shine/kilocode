import { describe, expect, test } from "bun:test"
import { KiloTerminalTitle } from "../../src/kilocode/cli/cmd/tui/terminal-title"

const base = "Kilo CLI"

function data(input: Partial<KiloTerminalTitle.Data> = {}): KiloTerminalTitle.Data {
  return {
    session: [{ id: "parent", title: "Build status" }],
    session_status: {},
    permission: {},
    question: {},
    suggestion: {},
    network: {},
    message: {},
    part: {},
    ...input,
  }
}

describe("KiloTerminalTitle", () => {
  test("format_noIndicator_returnsBaseTitle", () => {
    expect(KiloTerminalTitle.format({ base, indicator: "none" })).toBe("Kilo CLI")
  })

  test("format_workingPrefix_prependsThinkingIcon", () => {
    expect(KiloTerminalTitle.format({ base, title: "Build status", indicator: "working" })).toBe(
      "◔ Kilo CLI | Build status",
    )
  })

  test("format_attentionPrefix_prependsWarningIcon", () => {
    expect(KiloTerminalTitle.format({ base, title: "Build status", indicator: "attention" })).toBe(
      "⚠ Kilo CLI | Build status",
    )
  })

  test("format_finishedPrefix_prependsCheckIcon", () => {
    expect(KiloTerminalTitle.format({ base, title: "Build status", indicator: "finished" })).toBe(
      "✓ Kilo CLI | Build status",
    )
  })

  test("format_longSessionTitle_truncatesToExistingLimit", () => {
    expect(
      KiloTerminalTitle.format({
        base,
        title: "12345678901234567890123456789012345678901234567890",
        indicator: "working",
      }),
    ).toBe("◔ Kilo CLI | 1234567890123456789012345678901234567...")
  })

  test("session_newIdleSession_hasNoIndicator", () => {
    expect(
      KiloTerminalTitle.session({
        base,
        id: "parent",
        data: data(),
        done: {},
      }),
    ).toEqual({ title: "Kilo CLI | Build status", id: "parent", active: false, indicator: "none" })
  })

  test("session_busySession_isWorking", () => {
    expect(
      KiloTerminalTitle.session({
        base,
        id: "parent",
        data: data({ session_status: { parent: { type: "busy" } } }),
        done: {},
      }),
    ).toEqual({ title: "◔ Kilo CLI | Build status", id: "parent", active: true, indicator: "working" })
  })

  test("session_pendingPermission_overridesBusy", () => {
    expect(
      KiloTerminalTitle.session({
        base,
        id: "parent",
        data: data({
          session_status: { parent: { type: "busy" } },
          permission: { parent: [{}] },
        }),
        done: {},
      }).indicator,
    ).toBe("attention")
  })

  test("session_childQuestion_marksParentAttention", () => {
    expect(
      KiloTerminalTitle.session({
        base,
        id: "parent",
        data: data({
          session: [
            { id: "parent", title: "Build status" },
            { id: "child", title: "Child", parentID: "parent" },
          ],
          question: { child: [{ blocking: true }] },
        }),
        done: {},
      }).indicator,
    ).toBe("attention")
  })

  test("session_latestAssistantPlanExit_marksAttention", () => {
    expect(
      KiloTerminalTitle.session({
        base,
        id: "parent",
        data: data({
          message: { parent: [{ id: "m1", role: "assistant" }] },
          part: {
            m1: [{ type: "tool", tool: "plan_exit", state: { status: "completed" } }],
          },
        }),
        done: {},
      }).indicator,
    ).toBe("attention")
  })

  test("session_latestUserAfterPlanExit_clearsPlanExitAttention", () => {
    expect(
      KiloTerminalTitle.session({
        base,
        id: "parent",
        data: data({
          message: {
            parent: [
              { id: "m1", role: "assistant" },
              { id: "m2", role: "user" },
            ],
          },
          part: {
            m1: [{ type: "tool", tool: "plan_exit", state: { status: "completed" } }],
          },
        }),
        done: {},
      }).indicator,
    ).toBe("none")
  })

  test("session_doneIdleSession_isFinished", () => {
    expect(
      KiloTerminalTitle.session({
        base,
        id: "parent",
        data: data(),
        done: { parent: true },
      }).indicator,
    ).toBe("finished")
  })
})
