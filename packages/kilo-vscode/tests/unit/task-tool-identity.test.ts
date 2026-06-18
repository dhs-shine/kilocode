import { describe, expect, it } from "bun:test"
import path from "node:path"

const ROOT = path.resolve(import.meta.dir, "../..")

describe("task tool index identity", () => {
  it("preserves a child tool proxy across status updates", () => {
    const result = Bun.spawnSync(
      [
        "bun",
        "--conditions=browser",
        "-e",
        `
          import { createRoot } from "solid-js"
          import { createStore } from "solid-js/store"
          import {
            reconcileSessionToolParts,
            upsertSessionToolPart,
          } from "./webview-ui/src/context/session-utils.ts"

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
            setStore("tools", reconcileSessionToolParts(tools))
            if (store.tools[0] !== row) throw new Error("tool proxy was replaced")
            if (row.state.status !== "completed") throw new Error("tool proxy was not updated")
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
