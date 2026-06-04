import { afterAll, beforeEach, describe, expect, it } from "bun:test"
import * as fs from "fs"
import * as os from "os"
import * as path from "path"
import type { KiloClient } from "@kilocode/sdk/v2/client"
import { handleMessage } from "../../src/kilo-provider/model-state"

const root = fs.mkdtempSync(path.join(os.tmpdir(), "model-state-"))
const file = path.join(root, "model.json")
const client = {
  path: {
    get: async () => ({ data: { state: root } }),
  },
} as unknown as KiloClient

afterAll(() => {
  fs.rmSync(root, { recursive: true, force: true })
})

beforeEach(() => {
  fs.writeFileSync(file, JSON.stringify({ model: {} }))
})

describe("model state", () => {
  it("preserves concurrent selections for different agents", async () => {
    await Promise.all([
      handleMessage(
        "persistModelSelection",
        { agent: "code", providerID: "kilo", modelID: "vendor/new-live-model" },
        client,
        () => {},
      ),
      handleMessage(
        "persistModelSelection",
        { agent: "plan", providerID: "kilo", modelID: "kilo-auto/free" },
        client,
        () => {},
      ),
    ])

    const data = JSON.parse(fs.readFileSync(file, "utf-8")) as { model: Record<string, unknown> }
    expect(data.model).toEqual({
      code: { providerID: "kilo", modelID: "vendor/new-live-model" },
      plan: { providerID: "kilo", modelID: "kilo-auto/free" },
    })
  })

  it("waits for queued writes before hydrating selections", async () => {
    const api = fs.promises as unknown as { writeFile: (...args: unknown[]) => Promise<void> }
    const original = api.writeFile
    const gate = Promise.withResolvers<void>()
    const started = Promise.withResolvers<void>()
    const sent: unknown[] = []

    api.writeFile = async (...args: unknown[]) => {
      if (args[0] === file) {
        started.resolve()
        await gate.promise
      }
      await original(...args)
    }

    try {
      const pending = handleMessage(
        "persistModelSelection",
        { agent: "code", providerID: "kilo", modelID: "vendor/new-live-model" },
        client,
        () => {},
      )
      await started.promise

      const hydration = handleMessage("requestModelSelections", { revision: 3 }, client, (message) =>
        sent.push(message),
      )
      await Promise.resolve()
      expect(sent).toEqual([])

      gate.resolve()
      await Promise.all([pending, hydration])
    } finally {
      gate.resolve()
      api.writeFile = original
    }

    expect(sent).toEqual([
      {
        type: "modelSelectionsLoaded",
        selections: { code: { providerID: "kilo", modelID: "vendor/new-live-model" } },
        revision: 3,
      },
    ])
  })
})
