import { afterEach, describe, expect, test } from "bun:test"
import path from "path"
import * as Log from "@opencode-ai/core/util/log"
import { Global } from "@opencode-ai/core/global"
import { Server } from "../../../src/server/server"
import { Config } from "../../../src/config/config"
import { AppRuntime } from "../../../src/effect/app-runtime"
import { resetDatabase } from "../../fixture/db"
import { disposeAllInstances, tmpdir } from "../../fixture/fixture"

void Log.init({ print: false })

const original = Global.Path.config

type Overlay = {
  fields: Record<string, { source: string; inherited: boolean; overridden: boolean; value?: unknown }>
  collections: Record<string, Array<{ key: string; source: string; inherited: boolean; local?: unknown }>>
  targets: { project?: string; global?: string; active?: string }
}

afterEach(async () => {
  ;(Global.Path as { config: string }).config = original
  await AppRuntime.runPromise(Config.Service.use((svc) => svc.invalidate(true)))
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

async function json<T>(response: Response) {
  expect(response.status).toBe(200)
  return (await response.json()) as T
}

async function config(dir: string, value: unknown) {
  await Bun.write(path.join(dir, "kilo.json"), JSON.stringify(value, null, 2))
}

async function invalidate() {
  await AppRuntime.runPromise(Config.Service.use((svc) => svc.invalidate(true)))
}

describe("config overlay routes", () => {
  test.serial("marks global values inherited in project scope", async () => {
    await using global = await tmpdir()
    await using project = await tmpdir()
    ;(Global.Path as { config: string }).config = global.path
    await config(global.path, {
      model: "kilo/global-model",
      permission: { bash: "ask" },
      mcp: { shared: { type: "local", command: ["node", "shared.js"], enabled: true } },
    })
    await invalidate()

    const body = await json<Overlay>(await req(project.path, "/config/overlay?scope=project"))

    expect(body.fields.model).toMatchObject({ source: "global", inherited: true, overridden: false })
    expect(body.collections.permission.find((item) => item.key === "bash")).toMatchObject({
      source: "global",
      inherited: true,
    })
    expect(body.collections.mcp.find((item) => item.key === "shared")).toMatchObject({
      source: "global",
      inherited: true,
    })
  })

  test.serial("removes local scalar override and falls back to global", async () => {
    await using global = await tmpdir()
    await using project = await tmpdir({ config: { model: "kilo/project-model", username: "alice" } })
    ;(Global.Path as { config: string }).config = global.path
    await config(global.path, { model: "kilo/global-model" })
    await invalidate()

    await json(
      await req(project.path, "/config/overlay", {
        method: "PATCH",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ scope: "project", unset: [["model"]] }),
      }),
    )
    const body = await json<Overlay>(await req(project.path, "/config/overlay?scope=project"))
    const saved = (await Bun.file(path.join(project.path, "opencode.json")).json()) as Record<string, unknown>

    expect(body.fields.model).toMatchObject({ source: "global", inherited: true })
    expect(saved.model).toBeUndefined()
    expect(saved.username).toBe("alice")
  })

  test.serial("writes project mcp overrides without copying inherited servers", async () => {
    await using global = await tmpdir()
    await using project = await tmpdir()
    ;(Global.Path as { config: string }).config = global.path
    await config(global.path, {
      mcp: { shared: { type: "local", command: ["node", "shared.js"], enabled: true } },
    })
    await invalidate()

    await json(
      await req(project.path, "/config/overlay", {
        method: "PATCH",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          scope: "project",
          set: { mcp: { local: { type: "local", command: ["node", "local.js"], enabled: true } } },
        }),
      }),
    )

    const saved = (await Bun.file(path.join(project.path, ".kilo", "kilo.json")).json()) as {
      mcp: Record<string, unknown>
    }
    expect(Object.keys(saved.mcp)).toEqual(["local"])
  })

  test.serial("disables inherited mcp server with a minimal local override", async () => {
    await using global = await tmpdir()
    await using project = await tmpdir()
    ;(Global.Path as { config: string }).config = global.path
    await config(global.path, {
      mcp: { shared: { type: "local", command: ["node", "shared.js"], enabled: true } },
    })
    await invalidate()

    await json(
      await req(project.path, "/config/overlay", {
        method: "PATCH",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ scope: "project", set: { mcp: { shared: { enabled: false } } } }),
      }),
    )

    const saved = (await Bun.file(path.join(project.path, ".kilo", "kilo.json")).json()) as {
      mcp: Record<string, unknown>
    }
    expect(saved.mcp).toEqual({ shared: { enabled: false } })
  })
})
