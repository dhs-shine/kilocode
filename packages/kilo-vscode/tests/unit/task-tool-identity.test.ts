import { describe, expect, it } from "bun:test"
import fs from "node:fs"
import path from "node:path"

const ROOT = path.resolve(import.meta.dir, "../..")
const SESSION = path.join(ROOT, "webview-ui/src/context/session.tsx")
const TASK = path.join(ROOT, "webview-ui/src/components/chat/TaskToolExpanded.tsx")

describe("task tool index identity", () => {
  it("connects keyed reconciliation to indexed child rows", () => {
    const session = fs.readFileSync(SESSION, "utf8")
    const task = fs.readFileSync(TASK, "utf8")
    expect(session).toContain('setStore("toolParts", sessionID, reconcile(tools, { key: "id" }))')
    expect(task).toContain("<Index each={childToolParts()}>")
  })

  it("preserves a streamed child tool proxy across status updates", () => {
    const result = Bun.spawnSync(
      [
        "bun",
        "--conditions=browser",
        "-e",
        `
          import { createRoot } from "solid-js"
          import { createStore, reconcile } from "solid-js/store"
          import { upsertSessionToolPart } from "./webview-ui/src/context/session-utils.ts"

          const running = {
            id: "tool-1",
            type: "tool",
            tool: "read",
            state: { status: "running", input: { filePath: "src/app.ts" }, title: "Reading" },
          }
          const completed = {
            id: "tool-1",
            type: "tool",
            tool: "read",
            state: { status: "completed", input: { filePath: "src/app.ts" }, title: "Read", output: "done" },
          }

          createRoot((dispose) => {
            const [store, setStore] = createStore({ tools: [running] })
            const row = store.tools[0]
            const tools = upsertSessionToolPart(store.tools, completed, { id: "message-1", sessionID: "child-1" })
            setStore("tools", reconcile(tools, { key: "id" }))
            if (store.tools[0] !== row) throw new Error("tool proxy was replaced")
            if (store.tools[0].state.status !== "completed") throw new Error("tool proxy was not updated")
            dispose()
          })
        `,
      ],
      { cwd: ROOT, stdout: "pipe", stderr: "pipe" },
    )

    const output = result.stdout.toString() + result.stderr.toString()
    expect(result.exitCode, output).toBe(0)
  })
})
