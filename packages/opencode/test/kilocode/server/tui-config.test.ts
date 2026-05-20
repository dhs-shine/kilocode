import { afterEach, describe, expect, test } from "bun:test"
import path from "path"
import fs from "fs/promises"
import * as Log from "@opencode-ai/core/util/log"
import { Server } from "../../../src/server/server"
import { resetDatabase } from "../../fixture/db"
import { disposeAllInstances, tmpdir } from "../../fixture/fixture"

void Log.init({ print: false })

afterEach(async () => {
  await disposeAllInstances()
  await resetDatabase()
})

describe("TUI config routes", () => {
  test("gets effective project TUI config", async () => {
    await using tmp = await tmpdir({
      init: async (dir) => {
        const cfg = path.join(dir, ".kilo")
        await fs.mkdir(cfg, { recursive: true })
        await Bun.write(
          path.join(cfg, "tui.json"),
          JSON.stringify({ theme: "dracula", keybinds: { app_exit: "ctrl+q" } }, null, 2),
        )
      },
    })

    const response = await Server.Legacy().app.request("/tui/config", {
      headers: { "x-kilo-directory": tmp.path },
    })

    expect(response.status).toBe(200)
    const body = (await response.json()) as {
      theme?: string
      keybinds?: Record<string, string>
      plugin_origins?: unknown
    }
    expect(body.theme).toBe("dracula")
    expect(body.keybinds?.app_exit).toBe("ctrl+q")
    expect(body.keybinds?.leader).toBe("ctrl+x")
    expect(body.plugin_origins).toBeUndefined()
  })

  test("patches project TUI config", async () => {
    await using tmp = await tmpdir()

    const response = await Server.Legacy().app.request("/tui/config?scope=project", {
      method: "PATCH",
      headers: {
        "content-type": "application/json",
        "x-kilo-directory": tmp.path,
      },
      body: JSON.stringify({ theme: "nord" }),
    })

    expect(response.status).toBe(200)
    const body = (await response.json()) as { theme?: string }
    expect(body.theme).toBe("nord")

    const saved = await Bun.file(path.join(tmp.path, ".kilo", "tui.json")).json()
    expect(saved).toEqual({ theme: "nord" })
  })
})
