import { describe, expect } from "bun:test"
import { Bus } from "@/bus"
import { BackgroundProcess } from "@/kilocode/background-process"
import { SessionID } from "@/session/schema"
import { Effect } from "effect"
import { TestInstance } from "../fixture/fixture"
import { it } from "../lib/effect"

function update(sessionID: SessionID) {
  const state: { off?: () => void; timer?: ReturnType<typeof setTimeout> } = {}
  const promise = new Promise<BackgroundProcess.Info>((resolve, reject) => {
    state.timer = setTimeout(() => {
      state.off?.()
      reject(new Error("timed out waiting for process update"))
    }, 5_000)
    state.off = Bus.subscribe(BackgroundProcess.Event.Updated, (event) => {
      const info = event.properties.info
      if (info.sessionID !== sessionID) return
      if (!info.output.includes("tick")) return
      state.off?.()
      if (state.timer) clearTimeout(state.timer)
      resolve(info)
    })
  })
  return {
    promise,
    dispose() {
      state.off?.()
      if (state.timer) clearTimeout(state.timer)
    },
  }
}

describe("BackgroundProcess", () => {
  it.instance("starts, reports readiness, and stops a process", () =>
    Effect.gen(function* () {
      const test = yield* TestInstance
      const sessionID = SessionID.descending()
      const command = "printf 'ready\\n'; while true; do sleep 1; done"

      const info = yield* Effect.promise(() =>
        BackgroundProcess.start({
          sessionID,
          command,
          cwd: test.directory,
          description: "test server",
          ready: { pattern: "ready", timeout: 5_000 },
        }),
      )

      expect(info.status).toBe("ready")
      expect(info.output).toContain("ready")

      const list = yield* Effect.promise(() => BackgroundProcess.list({ sessionID }))
      expect(list.map((item) => item.id)).toContain(info.id)

      const stopped = yield* Effect.promise(() => BackgroundProcess.stop(info.id))
      expect(stopped?.status).toBe("stopped")
      expect(stopped?.exitCode).toBeUndefined()
      expect(stopped?.signal).toBe("SIGTERM")

      yield* Effect.promise(() => BackgroundProcess.stopSession(sessionID))
      const next = yield* Effect.promise(() => BackgroundProcess.list({ sessionID }))
      expect(next).toEqual([])
    }),
  )

  it.instance("publishes output updates from process callbacks", () =>
    Effect.gen(function* () {
      const test = yield* TestInstance
      const sessionID = SessionID.descending()
      const command = "printf 'ready\n'; sleep 0.2; printf 'tick\n'; while true; do sleep 1; done"
      const wait = update(sessionID)
      const info = yield* Effect.promise(() =>
        BackgroundProcess.start({
          sessionID,
          command,
          cwd: test.directory,
          ready: { pattern: "ready", timeout: 5_000 },
        }),
      )

      try {
        const event = yield* Effect.promise(() => wait.promise)
        expect(event.id).toBe(info.id)
        expect(event.output).toContain("tick")
      } finally {
        wait.dispose()
        yield* Effect.promise(() => BackgroundProcess.stop(info.id))
        yield* Effect.promise(() => BackgroundProcess.stopSession(sessionID))
      }
    }),
  )

  it.instance("rejects invalid readiness patterns before launching", () =>
    Effect.gen(function* () {
      const test = yield* TestInstance
      const sessionID = SessionID.descending()

      const err = yield* Effect.promise(async () => {
        try {
          await BackgroundProcess.start({
            sessionID,
            command: "printf 'ready\n'",
            cwd: test.directory,
            ready: { pattern: "[", timeout: 1_000 },
          })
        } catch (err) {
          return err
        }
      })

      expect(err).toBeInstanceOf(Error)
      expect((err as Error).message).toContain("Invalid ready pattern")

      const list = yield* Effect.promise(() => BackgroundProcess.list({ sessionID }))
      expect(list).toEqual([])
    }),
  )
})
