import { afterEach, describe, expect, test } from "bun:test"
import { Flag } from "@opencode-ai/core/flag/flag"
import { Server } from "../../../src/server/server"
import { SessionPaths } from "../../../src/server/routes/instance/httpapi/groups/session"
import { resetDatabase } from "../../fixture/db"
import { disposeAllInstances, tmpdir } from "../../fixture/fixture"

const flag = Flag.KILO_EXPERIMENTAL_HTTPAPI

afterEach(async () => {
  Flag.KILO_EXPERIMENTAL_HTTPAPI = flag
  await disposeAllInstances()
  await resetDatabase()
})

describe("POST /session/:sessionID/command local-review", () => {
  test("keeps invalid-base failures scoped to review validation", async () => {
    await using tmp = await tmpdir({ git: true, config: { formatter: false, lsp: false } })
    Flag.KILO_EXPERIMENTAL_HTTPAPI = true

    const app = Server.Default().app
    const headers = { "Content-Type": "application/json", "x-kilo-directory": tmp.path }
    const created = await app.request(SessionPaths.create, {
      method: "POST",
      headers,
      body: JSON.stringify({}),
    })
    expect(created.status).toBe(200)
    const session = (await created.json()) as { id: string }

    const failed = await app.request(SessionPaths.command.replace(":sessionID", session.id), {
      method: "POST",
      headers,
      body: JSON.stringify({
        command: "local-review",
        arguments: "__missing_local_review_base__",
      }),
    })
    expect(failed.status).not.toBe(200)
    const body = (await failed.json()) as { name: string; data: { message: string } }
    expect(body.data.message).toContain(
      'Base branch or ref not found or has no common history: "__missing_local_review_base__"',
    )
    expect(body.data.message).not.toContain("No context found for instance")

    const history = await app.request(SessionPaths.messages.replace(":sessionID", session.id), { headers })
    expect(history.status).toBe(200)
    expect(await history.json()).toEqual([])
  })
})
