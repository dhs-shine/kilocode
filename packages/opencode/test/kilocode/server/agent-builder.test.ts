import { afterEach, describe, expect, test } from "bun:test"
import path from "path"
import * as Log from "@opencode-ai/core/util/log"
import { Server } from "../../../src/server/server"
import { resetDatabase } from "../../fixture/db"
import { disposeAllInstances, tmpdir } from "../../fixture/fixture"

void Log.init({ print: false })

type Output = {
  id: string
  scope: "global" | "project"
  path: string
  markdown: string
}

type Agent = {
  name: string
  mode: "primary" | "subagent" | "all"
  prompt: string
}

afterEach(async () => {
  await disposeAllInstances()
  await resetDatabase()
})

function req(dir: string, input: string, init?: RequestInit) {
  return Server.Legacy().app.request(input, {
    ...init,
    headers: {
      "x-kilo-directory": dir,
      ...init?.headers,
    },
  })
}

describe("agent builder routes", () => {
  test("previews and saves project agent markdown", async () => {
    await using tmp = await tmpdir()
    const body = {
      id: "reviewer",
      scope: "project",
      description: "Review code",
      mode: "subagent",
      model: "kilo/gpt-5.5",
      tools: ["read", "grep"],
      prompt: "Review the current diff and report risks.",
    }

    const preview = await req(tmp.path, "/agent-builder/preview", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(body),
    })

    expect(preview.status).toBe(200)
    const draft = (await preview.json()) as Output
    expect(draft.markdown).toContain('description: "Review code"')
    expect(draft.markdown).toContain('mode: "subagent"')
    expect(draft.markdown).toContain('permission: {"read":"allow","grep":"allow"}')

    const saved = await req(tmp.path, "/agent-builder/reviewer", {
      method: "PUT",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(body),
    })

    expect(saved.status).toBe(200)
    const output = (await saved.json()) as Output
    expect(output.path).toBe(path.join(tmp.path, ".kilo", "agent", "reviewer.md"))
    expect(await Bun.file(output.path).text()).toBe(output.markdown)

    const agents = (await (await req(tmp.path, "/agent")).json()) as Agent[]
    expect(agents.find((item) => item.name === "reviewer")).toMatchObject({
      mode: "subagent",
      prompt: "Review the current diff and report risks.",
    })
  })

  test("saves without a duplicated body id", async () => {
    await using tmp = await tmpdir()
    const saved = await req(tmp.path, "/agent-builder/canonical", {
      method: "PUT",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        scope: "project",
        prompt: "Use the route id for storage.",
      }),
    })

    expect(saved.status).toBe(200)
    const output = (await saved.json()) as Output
    expect(output.id).toBe("canonical")
    expect(output.path).toBe(path.join(tmp.path, ".kilo", "agent", "canonical.md"))
    expect(await Bun.file(output.path).exists()).toBe(true)
  })

  test("rejects whitespace-only prompts", async () => {
    await using tmp = await tmpdir()
    const preview = await req(tmp.path, "/agent-builder/preview", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        id: "empty",
        scope: "project",
        prompt: "   ",
      }),
    })

    expect(preview.status).toBe(400)
  })
})
