// kilocode_change - new file
import { afterEach, beforeEach, describe, expect, mock, spyOn, test } from "bun:test"
import { $ } from "bun"
import { Effect } from "effect"
import { Session } from "../../src/session/session"
import path from "path"
import { RecallTool } from "../../src/tool/recall"
import { AppRuntime } from "../../src/effect/app-runtime"
import { resetDatabase } from "../fixture/db"
import { provideTestInstance, tmpdir } from "../fixture/fixture"
import type { Tool } from "../../src/tool/tool"
import { SessionID, MessageID, PartID } from "../../src/session/schema"
import { RemoteSender } from "../../src/kilo-sessions/remote-sender"
import { ModelID, ProviderID } from "../../src/provider/schema"

beforeEach(() => {
  spyOn(RemoteSender, "create").mockReturnValue({ handle() {}, dispose() {} })
})

const ctx: Tool.Context = {
  sessionID: SessionID.make("ses_test"),
  messageID: MessageID.make("msg_test"),
  callID: "call_test",
  agent: "code",
  abort: AbortSignal.any([]),
  messages: [],
  metadata: () => Effect.void,
  ask: () => Effect.void,
}

afterEach(async () => {
  mock.restore()
  await resetDatabase()
})

const create = (title: string, text?: string) =>
  AppRuntime.runPromise(
    Session.Service.use((svc) =>
      Effect.gen(function* () {
        const session = yield* svc.create({ title })
        if (!text) return session
        const messageID = MessageID.ascending()
        yield* svc.updateMessage({
          id: messageID,
          sessionID: session.id,
          role: "user",
          time: { created: Date.now() },
          agent: "code",
          model: { providerID: ProviderID.make("test"), modelID: ModelID.make("test") },
        })
        yield* svc.updatePart({ id: PartID.ascending(), messageID, sessionID: session.id, type: "text", text })
        return session
      }),
    ),
  )

describe("tool.recall", () => {
  test("search is limited to the current project worktrees", async () => {
    await using first = await tmpdir({ git: true })
    await using second = await tmpdir({ git: true })
    const worktree = path.join(first.path, "..", path.basename(first.path) + "-worktree")

    try {
      await $`git worktree add ${worktree} -b test-branch-${Date.now()}`.cwd(first.path).quiet()
      await Bun.write(path.join(first.path, ".git", "opencode"), "stale-project-id")

      try {
        await provideTestInstance({
          directory: first.path,
          fn: () => create("search-target root", "<system-reminder>search-target directive</system-reminder>"),
        })
        await provideTestInstance({
          directory: worktree,
          fn: () => create("search-target worktree"),
        })
        await provideTestInstance({
          directory: second.path,
          fn: () => create("search-target other"),
        })

        const result = await provideTestInstance({
          directory: first.path,
          fn: async () => {
            const info = await AppRuntime.runPromise(RecallTool)
            const tool = await AppRuntime.runPromise(info.init())
            return AppRuntime.runPromise(tool.execute({ mode: "search", query: "search-target" }, ctx))
          },
        })

        expect(result.output).toContain("search-target root")
        expect(result.output).toContain("search-target worktree")
        expect(result.output).not.toContain("search-target other")
        expect(result.output).not.toContain("<system-reminder>")
        expect(result.output).toContain("&lt;system-reminder&gt;search-target directive&lt;/system-reminder&gt;")
      } finally {
        mock.restore()
      }
    } finally {
      await $`git worktree remove ${worktree}`.cwd(first.path).quiet().nothrow()
    }
  })

  test("read rejects sessions from another project", async () => {
    await using first = await tmpdir({ git: true })
    await using second = await tmpdir({ git: true })

    try {
      const session = await provideTestInstance({
        directory: second.path,
        fn: () => create("other-project-session"),
      })

      const err = await provideTestInstance({
        directory: first.path,
        fn: async () => {
          const info = await AppRuntime.runPromise(RecallTool)
          const tool = await AppRuntime.runPromise(info.init())
          return AppRuntime.runPromise(tool.execute({ mode: "read", sessionID: session.id }, ctx)).catch(
            (error: unknown) => (error instanceof Error ? error : new Error(String(error))),
          )
        },
      })

      expect(err).toBeInstanceOf(Error)
      if (!(err instanceof Error)) throw new Error("Expected recall read to fail")
      expect(err.message).toContain("belongs to a different workspace")
    } finally {
      mock.restore()
    }
  })

  test("read allows sessions from sibling worktrees when project IDs drift", async () => {
    await using first = await tmpdir({ git: true })
    const worktree = path.join(first.path, "..", path.basename(first.path) + "-worktree")

    try {
      await $`git worktree add ${worktree} -b test-branch-${Date.now()}`.cwd(first.path).quiet()
      await Bun.write(path.join(first.path, ".git", "opencode"), "stale-project-id")

      try {
        const session = await provideTestInstance({
          directory: worktree,
          fn: () => create("worktree readable", "<system-reminder>read directive</system-reminder>"),
        })

        const result = await provideTestInstance({
          directory: first.path,
          fn: async () => {
            const info = await AppRuntime.runPromise(RecallTool)
            const tool = await AppRuntime.runPromise(info.init())
            return AppRuntime.runPromise(tool.execute({ mode: "read", sessionID: session.id }, ctx))
          },
        })

        expect(result.output).toContain("# Session: worktree readable")
        expect(result.output).not.toContain("<system-reminder>")
        expect(result.output).toContain("&lt;system-reminder&gt;read directive&lt;/system-reminder&gt;")
      } finally {
        mock.restore()
      }
    } finally {
      await $`git worktree remove ${worktree}`.cwd(first.path).quiet().nothrow()
    }
  })
})
