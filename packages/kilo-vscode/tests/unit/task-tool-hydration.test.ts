import { beforeEach, describe, expect, it } from "bun:test"
import { initialOpen } from "@kilocode/kilo-ui/basic-tool"
import { resetToolOpenState, toolOpenKey, writeToolOpen } from "../../../kilo-ui/src/components/tool-open-state"
import { taskResult, taskRunning, taskVisible } from "../../webview-ui/src/components/chat/task-tool-state"

describe("completed task hydration", () => {
  beforeEach(() => resetToolOpenState())

  it("opens running tasks and collapses completed tasks by default", () => {
    expect(taskRunning("pending")).toBe(true)
    expect(taskRunning("running")).toBe(true)
    expect(taskRunning("completed")).toBe(false)
    expect(initialOpen({ tool: "task", partID: "part-new", defaultOpen: taskRunning("completed") })).toBe(false)
  })

  it("keeps expansion state isolated by copied part ID", () => {
    const source = { tool: "task", partID: "part-source", defaultOpen: false }
    const fork = { tool: "task", partID: "part-fork", defaultOpen: false }
    writeToolOpen(toolOpenKey(source), true)

    expect(initialOpen(source)).toBe(true)
    expect(initialOpen(fork)).toBe(false)
  })

  it("hydrates and streams a child only while expanded", () => {
    expect(taskVisible(false, "ses_child")).toBeUndefined()
    expect(taskVisible(true, "ses_child")).toBe("ses_child")
    expect(taskVisible(true, undefined)).toBeUndefined()
  })

  it("renders the retained result when a fork has no child session", () => {
    const output = "task_id: stale\n\n<task_result>\nchild outcome\n</task_result>"
    expect(taskResult(output, undefined)).toBe("child outcome")
    expect(taskResult(output, "ses_child")).toBeUndefined()
    expect(taskResult("plain output", undefined)).toBe("plain output")
  })
})
