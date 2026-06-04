import { describe, expect, it } from "bun:test"

// vscode mock is provided by the shared preload (tests/setup/vscode-mock.ts)
const { KiloProvider } = await import("../../src/KiloProvider")

type Internals = {
  webview: { postMessage: (message: unknown) => Promise<unknown> } | null
  isWebviewReady: boolean
  syncWebviewState: () => Promise<void>
  handleWebviewReady: () => Promise<void>
  flushPendingKiloModel: () => void
}

function connection() {
  const state = { connected: true }
  return {
    state,
    getClient: () => {
      if (!state.connected) throw new Error("Not connected")
      return {}
    },
  }
}

describe("KiloProvider promoted model selection", () => {
  it("flushes a queued model before ancillary webview sync can fail", async () => {
    const service = connection()
    const provider = new KiloProvider({} as never, service as never)
    const internal = provider as unknown as Internals
    const sent: unknown[] = []

    internal.webview = {
      postMessage: async (message) => {
        sent.push(message)
        return true
      },
    }
    internal.syncWebviewState = async () => {
      throw new Error("profile unavailable")
    }

    provider.selectKiloModel("stealth/claude-opus-4.8")
    expect(sent).toEqual([])

    await expect(internal.handleWebviewReady()).rejects.toThrow("profile unavailable")
    expect(sent).toEqual([{ type: "selectKiloModel", modelID: "stealth/claude-opus-4.8" }])
  })

  it("keeps a queued model until the backend client is available", () => {
    const service = connection()
    service.state.connected = false
    const provider = new KiloProvider({} as never, service as never)
    const internal = provider as unknown as Internals
    const sent: unknown[] = []

    internal.webview = {
      postMessage: async (message) => {
        sent.push(message)
        return true
      },
    }
    internal.isWebviewReady = true

    provider.selectKiloModel("stealth/claude-opus-4.8")
    expect(sent).toEqual([])

    service.state.connected = true
    internal.flushPendingKiloModel()
    expect(sent).toEqual([{ type: "selectKiloModel", modelID: "stealth/claude-opus-4.8" }])
  })
})
