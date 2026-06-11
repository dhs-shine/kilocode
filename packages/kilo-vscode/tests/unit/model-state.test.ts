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
})
