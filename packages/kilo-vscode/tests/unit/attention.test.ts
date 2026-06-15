import { describe, expect, it } from "bun:test"
import type { TuiAttentionSoundName } from "@kilocode/plugin/tui"
import { AttentionService } from "../../src/services/attention/service"
import type { KiloConnectionService } from "../../src/services/cli-backend/connection-service"
import type { SSEPayload } from "../../src/services/cli-backend/sdk-sse-adapter"

function setup() {
  const sounds: TuiAttentionSoundName[] = []
  const events: Array<(event: SSEPayload) => void> = []
  const states: Array<(state: "connecting" | "connected" | "disconnected" | "error") => void> = []
  const connection = {
    onEvent: (handler: (event: SSEPayload) => void) => {
      events.push(handler)
      return () => undefined
    },
    onStateChange: (handler: (state: "connecting" | "connected" | "disconnected" | "error") => void) => {
      states.push(handler)
      return () => undefined
    },
  } as unknown as KiloConnectionService
  const service = new AttentionService(connection)
  ;(service as unknown as { notify: (sound: TuiAttentionSoundName) => void }).notify = (sound) => sounds.push(sound)
  return {
    sounds,
    event: (event: SSEPayload) => events[0]?.(event),
    state: (state: "connecting" | "connected" | "disconnected" | "error") => states[0]?.(state),
    service,
  }
}

function event(value: unknown) {
  return value as SSEPayload
}

describe("AttentionService", () => {
  it("plays the upstream completion sound once after active becomes idle", () => {
    const test = setup()
    test.event(event({ type: "session.status", properties: { sessionID: "s1", status: { type: "busy" } } }))
    test.event(event({ type: "session.status", properties: { sessionID: "s1", status: { type: "idle" } } }))
    test.event(event({ type: "session.status", properties: { sessionID: "s1", status: { type: "idle" } } }))

    expect(test.sounds).toEqual(["done"])
    test.service.dispose()
  })

  it("uses the upstream subagent completion sound", () => {
    const test = setup()
    test.event(
      event({
        type: "sync",
        name: "session.created.1",
        data: { sessionID: "child", info: { parentID: "parent" } },
      }),
    )
    test.event(
      event({ type: "sync", name: "session.updated.1", data: { sessionID: "child", info: { title: "work" } } }),
    )
    test.event(event({ type: "session.status", properties: { sessionID: "child", status: { type: "retry" } } }))
    test.event(event({ type: "session.status", properties: { sessionID: "child", status: { type: "idle" } } }))

    expect(test.sounds).toEqual(["subagent_done"])
    test.service.dispose()
  })

  it("deduplicates question and permission requests", () => {
    const test = setup()
    test.event(event({ type: "question.asked", properties: { id: "q1", sessionID: "s1" } }))
    test.event(event({ type: "question.asked", properties: { id: "q1", sessionID: "s1" } }))
    test.event(event({ type: "question.replied", properties: { requestID: "q1", sessionID: "s1" } }))
    test.event(event({ type: "question.asked", properties: { id: "q1", sessionID: "s1" } }))
    test.event(event({ type: "permission.asked", properties: { id: "p1", sessionID: "s1" } }))
    test.event(event({ type: "permission.asked", properties: { id: "p1", sessionID: "s1" } }))

    expect(test.sounds).toEqual(["question", "question", "permission"])
    test.service.dispose()
  })

  it("plays the error sound and suppresses the following completion", () => {
    const test = setup()
    test.event(event({ type: "session.status", properties: { sessionID: "s1", status: { type: "busy" } } }))
    test.event(event({ type: "session.error", properties: { sessionID: "s1", error: { name: "ApiError" } } }))
    test.event(event({ type: "session.status", properties: { sessionID: "s1", status: { type: "idle" } } }))

    expect(test.sounds).toEqual(["error"])
    test.service.dispose()
  })

  it("clears transitions when the backend disconnects", () => {
    const test = setup()
    test.event(event({ type: "session.status", properties: { sessionID: "s1", status: { type: "busy" } } }))
    test.state("disconnected")
    test.event(event({ type: "session.status", properties: { sessionID: "s1", status: { type: "idle" } } }))

    expect(test.sounds).toEqual([])
    test.service.dispose()
  })
})

describe("attention defaults", () => {
  it("keeps OpenCode attention sounds opt-in", async () => {
    const manifest = (await Bun.file(new URL("../../package.json", import.meta.url)).json()) as {
      contributes: { configuration: { properties: Record<string, { default?: unknown }> } }
    }
    const properties = manifest.contributes.configuration.properties

    expect(properties["kilo-code.new.attention.enabled"]?.default).toBe(false)
    expect(properties["kilo-code.new.sounds.agentEnabled"]).toBeUndefined()
    expect(properties["kilo-code.new.sounds.permissionsEnabled"]).toBeUndefined()
    expect(properties["kilo-code.new.sounds.errorsEnabled"]).toBeUndefined()
  })

  it("packages the upstream default sound set", async () => {
    const names = ["bip-bop-01", "bip-bop-03", "staplebops-06", "nope-03", "yup-01"]
    const exists = await Promise.all(
      names.map((name) => Bun.file(new URL(`../../audio-wav/${name}.wav`, import.meta.url)).exists()),
    )
    expect(exists).toEqual([true, true, true, true, true])
  })
})
