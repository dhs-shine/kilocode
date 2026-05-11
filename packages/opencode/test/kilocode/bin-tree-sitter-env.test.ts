import { describe, expect, test } from "bun:test"
import { mkdir, writeFile } from "fs/promises"
import { tmpdir } from "os"
import { join } from "path"

const script = join(import.meta.dir, "..", "..", "bin", "kilo")

describe("bin/kilo tree-sitter resources", () => {
  async function setup(root: string, nested: boolean) {
    const dir = nested
      ? join(root, "node_modules", "@kilocode", "cli-darwin-arm64", "bin")
      : join(root, "node_modules", "@kilocode", "cli", "bin")
    const wasm = join(dir, "tree-sitter")
    const bin = join(dir, nested ? "kilo" : ".kilo")
    const log = join(root, nested ? "nested-env.txt" : "cached-env.txt")

    await mkdir(wasm, { recursive: true })
    await writeFile(join(wasm, "tree-sitter.wasm"), "wasm")
    await writeFile(bin, `#!/bin/sh\nprintf '%s' "$KILO_TREE_SITTER_WASM_DIR" > ${JSON.stringify(log)}\n`, {
      mode: 0o755,
    })

    return { bin, log, wasm }
  }

  async function run(root: string, bin: string) {
    return Bun.spawnSync(["node", "--input-type=commonjs", "--eval", await Bun.file(script).text()], {
      cwd: root,
      env: {
        PATH: process.env.PATH ?? "",
        KILO_BIN_PATH: bin,
      },
    })
  }

  test("exports co-located tree-sitter WASM dir for optional package binary", async () => {
    const root = await Bun.$`mktemp -d ${join(tmpdir(), "kilo-bin-tree-sitter-XXXXXX")}`
      .text()
      .then((text) => text.trim())
    try {
      const item = await setup(root, true)
      const proc = await run(root, item.bin)

      expect(proc.exitCode).toBe(0)
      expect(await Bun.file(item.log).text()).toBe(item.wasm)
    } finally {
      await Bun.$`rm -rf ${root}`
    }
  })

  test("exports co-located tree-sitter WASM dir for cached postinstall binary", async () => {
    const root = await Bun.$`mktemp -d ${join(tmpdir(), "kilo-bin-tree-sitter-XXXXXX")}`
      .text()
      .then((text) => text.trim())
    try {
      const item = await setup(root, false)
      const proc = await run(root, item.bin)

      expect(proc.exitCode).toBe(0)
      expect(await Bun.file(item.log).text()).toBe(item.wasm)
    } finally {
      await Bun.$`rm -rf ${root}`
    }
  })
})
