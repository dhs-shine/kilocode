import { describe, expect, it } from "bun:test"
import type { Config } from "@kilocode/sdk/v2/client"

// vscode mock is provided by the shared preload (tests/setup/vscode-mock.ts)
const { KiloProvider } = await import("../../src/KiloProvider")

type Internals = {
  connectionState: "connecting" | "connected" | "disconnected" | "error"
  currentSession: { id: string } | null
  cachedIndexingStatusMessage: unknown
  pending: number
  configRevision: number
  refreshWait: number
  postMessage: (message: unknown) => void
  handleEvent: (event: unknown, directory?: string) => void
  reloadAfterAuthChange: () => Promise<void>
  handleUpdateConfig: (
    partial: Partial<Config>,
    project?: Partial<Config>,
    globalUnset?: string[][],
    projectUnset?: string[][],
  ) => Promise<void>
  fetchAndSendGlobalConfig: () => Promise<void>
  fetchAndSendConfigUpdated: () => Promise<void>
  fetchAndSendConfig: () => Promise<void>
  fetchAndSendProviders: () => Promise<void>
  fetchAndSendAgents: () => Promise<void>
  fetchAndSendSkills: () => Promise<void>
  fetchAndSendCommands: () => Promise<void>
  fetchAndSendNotifications: () => Promise<void>
  fetchAndSendIndexingStatus: () => Promise<void>
}

function createConnection() {
  let drains = 0
  const patches: unknown[] = []
  const client = {
    global: {
      config: {
        get: async () => ({ data: {} }),
        update: async () => ({ data: {} }),
      },
    },
    config: {
      get: async () => ({ data: {} }),
      update: async () => ({ data: {} }),
      overlay: async () => ({ data: { project: {} } }),
      overlayUpdate: async (patch: unknown) => {
        patches.push(patch)
        return { data: {} }
      },
    },
  }

  return {
    client,
    drains: () => drains,
    patches: () => patches,
    service: {
      drainPendingPrompts: async () => {
        drains += 1
      },
      getClient: () => client,
    },
  }
}

describe("KiloProvider indexing refresh", () => {
  it("reloadAfterAuthChange fetches config first, then indexing status", async () => {
    const provider = new KiloProvider({} as never, {} as never)
    const internal = provider as unknown as Internals
    const calls: string[] = []

    internal.fetchAndSendConfig = async () => {
      calls.push("config")
    }
    internal.fetchAndSendProviders = async () => {
      calls.push("providers")
    }
    internal.fetchAndSendAgents = async () => {
      calls.push("agents")
    }
    internal.fetchAndSendSkills = async () => {
      calls.push("skills")
    }
    internal.fetchAndSendCommands = async () => {
      calls.push("commands")
    }
    internal.fetchAndSendNotifications = async () => {
      calls.push("notifications")
    }
    internal.fetchAndSendIndexingStatus = async () => {
      calls.push("indexing")
    }

    await internal.reloadAfterAuthChange()

    expect(calls[0]).toBe("config")
    expect(calls.includes("indexing")).toBe(true)
  })

  it("handleUpdateConfig no longer eagerly fetches indexing status", async () => {
    const conn = createConnection()
    const provider = new KiloProvider({} as never, conn.service as never)
    const internal = provider as unknown as Internals

    let indexing = 0
    internal.connectionState = "connected"
    internal.fetchAndSendIndexingStatus = async () => {
      indexing += 1
    }

    await internal.handleUpdateConfig({})

    expect(conn.drains()).toBe(1)
    expect(indexing).toBe(0)
  })

  it("confirms saved config when the post-write refresh stalls", async () => {
    const conn = createConnection()
    conn.client.config.get = async () => new Promise<never>(() => {})
    const provider = new KiloProvider({} as never, conn.service as never)
    const internal = provider as unknown as Internals
    const messages: Array<{ type?: string; writes: number }> = []

    internal.connectionState = "connected"
    internal.refreshWait = 0
    internal.postMessage = (message) =>
      messages.push({ ...(message as { type?: string }), writes: conn.patches().length })

    await internal.handleUpdateConfig(
      { indexing: { provider: "kilo" } },
      { commit_message: { prompt: "Use conventional commits" } },
    )

    expect(messages).toEqual([{ type: "configSaved", writes: 2 }])
    expect(internal.pending).toBe(0)
  })

  it("does not confirm a partially written scoped save", async () => {
    const conn = createConnection()
    conn.client.config.overlayUpdate = async (patch: unknown) => {
      conn.patches().push(patch)
      if ((patch as { scope?: string }).scope === "project") throw new Error("project write failed")
      return { data: {} }
    }
    const provider = new KiloProvider({} as never, conn.service as never)
    const internal = provider as unknown as Internals
    const messages: Array<{ type?: string }> = []

    internal.connectionState = "connected"
    internal.postMessage = (message) => messages.push(message as { type?: string })

    await internal.handleUpdateConfig({ snapshot: true }, { commit_message: { prompt: "test" } })

    expect(messages.map((message) => message.type)).toEqual(["configUpdated", "configUpdateFailed"])
    expect(internal.pending).toBe(0)
  })

  it("reports a partial write failure when the recovery refresh stalls", async () => {
    const conn = createConnection()
    conn.client.config.overlayUpdate = async (patch: unknown) => {
      conn.patches().push(patch)
      if ((patch as { scope?: string }).scope === "project") throw new Error("project write failed")
      return { data: {} }
    }
    conn.client.config.get = async () => new Promise<never>(() => {})
    const provider = new KiloProvider({} as never, conn.service as never)
    const internal = provider as unknown as Internals
    const messages: Array<{ type?: string }> = []

    internal.connectionState = "connected"
    internal.refreshWait = 0
    internal.postMessage = (message) => messages.push(message as { type?: string })

    await internal.handleUpdateConfig({ snapshot: true }, { commit_message: { prompt: "test" } })

    expect(messages.map((message) => message.type)).toEqual(["configUpdateFailed"])
    expect(internal.pending).toBe(0)
  })

  it("drops a stale config refresh that finishes after a newer one", async () => {
    const conn = createConnection()
    let release!: () => void
    const gate = new Promise<void>((resolve) => {
      release = resolve
    })
    let calls = 0
    conn.client.config.get = async () => {
      calls += 1
      if (calls === 1) {
        await gate
        return { data: { snapshot: true } }
      }
      return { data: { snapshot: false } }
    }
    const provider = new KiloProvider({} as never, conn.service as never)
    const internal = provider as unknown as Internals
    const messages: Array<{ type?: string; config?: Config }> = []

    internal.connectionState = "connected"
    internal.postMessage = (message) => messages.push(message as { type?: string; config?: Config })

    const stale = internal.fetchAndSendConfigUpdated()
    await Bun.sleep(0)
    await internal.fetchAndSendConfigUpdated()
    release()
    await stale

    expect(messages).toHaveLength(1)
    expect(messages[0]?.config?.snapshot).toBe(false)
    expect(internal.configRevision).toBe(2)
  })

  it("drops a stale global-only config response", async () => {
    const conn = createConnection()
    let release!: () => void
    const gate = new Promise<void>((resolve) => {
      release = resolve
    })
    conn.client.global.config.get = async () => {
      await gate
      return { data: { snapshot: true } }
    }
    const provider = new KiloProvider({} as never, conn.service as never)
    const internal = provider as unknown as Internals
    const messages: Array<{ type?: string }> = []

    internal.connectionState = "connected"
    internal.postMessage = (message) => messages.push(message as { type?: string })

    const stale = internal.fetchAndSendGlobalConfig()
    await Bun.sleep(0)
    internal.configRevision++
    release()
    await stale

    expect(messages).toHaveLength(0)
  })

  it("refreshes providers when prompt-training model visibility changes", async () => {
    const conn = createConnection()
    const provider = new KiloProvider({} as never, conn.service as never)
    const internal = provider as unknown as Internals
    let calls = 0
    internal.connectionState = "connected"
    internal.fetchAndSendProviders = async () => {
      calls += 1
    }

    await internal.handleUpdateConfig({ hide_prompt_training_models: true })

    expect(calls).toBe(1)
  })

  it("passes scoped unset paths to the config overlay endpoint", async () => {
    const conn = createConnection()
    const provider = new KiloProvider({} as never, conn.service as never)
    const internal = provider as unknown as Internals
    internal.connectionState = "connected"

    await internal.handleUpdateConfig(
      { indexing: { qdrant: { apiKey: undefined } } },
      { indexing: { searchMinScore: undefined } },
      [["indexing", "qdrant", "apiKey"]],
      [["indexing", "searchMinScore"]],
    )

    expect(conn.patches()).toEqual([
      expect.objectContaining({
        scope: "global",
        set: { indexing: { qdrant: { apiKey: undefined } } },
        unset: [["indexing", "qdrant", "apiKey"]],
      }),
      expect.objectContaining({
        scope: "project",
        set: { indexing: { searchMinScore: undefined } },
        unset: [["indexing", "searchMinScore"]],
      }),
    ])
  })

  it("fetchAndSendIndexingStatus uses current session directory header", async () => {
    const worktree = "/repo/.kilo/.kilocode/worktrees/feature"
    const calls: { input: RequestInfo | URL; init?: RequestInit }[] = []
    const original = globalThis.fetch

    globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
      calls.push({ input, init })
      return new Response(
        JSON.stringify({
          state: "Disabled",
          message: "Indexing is disabled in worktree sessions.",
          processedFiles: 0,
          totalFiles: 0,
          percent: 0,
        }),
        {
          status: 200,
          headers: { "content-type": "application/json" },
        },
      )
    }) as typeof fetch

    try {
      const provider = new KiloProvider(
        {} as never,
        {
          getClient: () => ({}) as never,
          getServerConfig: () => ({ baseUrl: "http://127.0.0.1:9999", password: "secret" }),
        } as never,
      )
      const internal = provider as unknown as Internals
      provider.setSessionDirectory("ses_worktree", worktree)
      internal.currentSession = { id: "ses_worktree" }

      await internal.fetchAndSendIndexingStatus()

      expect(calls.length).toBe(1)
      const headers = new Headers(calls[0]?.init?.headers)
      const auth = Buffer.from("kilo:secret").toString("base64")
      expect(headers.get("Authorization")).toBe(`Basic ${auth}`)
      expect(headers.get("x-kilo-directory")).toBe(worktree)
    } finally {
      globalThis.fetch = original
    }
  })

  it("forwards indexing.status when directory only differs by Windows drive casing", () => {
    const provider = new KiloProvider(
      {} as never,
      {
        resolveEventSessionId: () => undefined,
      } as never,
    )
    const internal = provider as unknown as Internals
    provider.setSessionDirectory("ses_worktree", "C:/Repo/Work")
    internal.currentSession = { id: "ses_worktree" }

    const desc = Object.getOwnPropertyDescriptor(process, "platform")
    Object.defineProperty(process, "platform", { value: "win32", configurable: true })
    try {
      internal.handleEvent(
        {
          type: "indexing.status",
          properties: {
            status: {
              state: "Complete",
              message: "Done",
              processedFiles: 10,
              totalFiles: 10,
              percent: 100,
            },
          },
        },
        "c:/repo/work",
      )
    } finally {
      if (desc) Object.defineProperty(process, "platform", desc)
    }

    const msg = internal.cachedIndexingStatusMessage as { type?: string; status?: { state?: string } } | undefined
    expect(msg?.type).toBe("indexingStatusLoaded")
    expect(msg?.status?.state).toBe("Complete")
  })
})
