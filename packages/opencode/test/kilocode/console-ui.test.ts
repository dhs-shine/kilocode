import { afterEach, describe, expect, test } from "bun:test"
import path from "path"
import { mkdir } from "fs/promises"
import { tmpdir } from "../fixture/fixture"
import { serveUI } from "../../src/server/routes/ui"

const original = process.env.KILO_CONSOLE_ASSET_DIR

afterEach(() => {
  if (original === undefined) delete process.env.KILO_CONSOLE_ASSET_DIR
  else process.env.KILO_CONSOLE_ASSET_DIR = original
})

async function assets(dir: string) {
  await mkdir(path.join(dir, "assets"), { recursive: true })
  await Bun.write(path.join(dir, "index.html"), '<!doctype html><html><body><div id="root">console</div></body></html>')
  await Bun.write(path.join(dir, "assets", "app.js"), "console.log('kilo')")
}

describe("Kilo Console UI routes", () => {
  test("serves the console index for /console and SPA routes", async () => {
    await using tmp = await tmpdir()
    process.env.KILO_CONSOLE_ASSET_DIR = tmp.path
    await assets(tmp.path)

    const root = await serveUI(new Request("http://localhost/console"))
    expect(root.status).toBe(200)
    expect(root.headers.get("content-type")).toContain("text/html")
    expect(await root.text()).toContain("console")

    const route = await serveUI(new Request("http://localhost/console/projects/demo"))
    expect(route.status).toBe(200)
    expect(await route.text()).toContain("console")
  })

  test("serves console assets without falling back on missing files", async () => {
    await using tmp = await tmpdir()
    process.env.KILO_CONSOLE_ASSET_DIR = tmp.path
    await assets(tmp.path)

    const asset = await serveUI(new Request("http://localhost/console/assets/app.js"))
    expect(asset.status).toBe(200)
    expect(await asset.text()).toContain("kilo")

    const missing = await serveUI(new Request("http://localhost/console/assets/missing.js"))
    expect(missing.status).toBe(404)
  })
})
