import fs from "node:fs/promises"
import os from "node:os"
import path from "node:path"
import { expect, test } from "bun:test"
import { ConfigVariable } from "@/config/variable"

const source = { type: "virtual" as const, source: "test", dir: process.cwd() }

test("does not substitute server credentials from the environment", async () => {
  const result = await ConfigVariable.substitute({
    ...source,
    text: "password={env:KILO_SERVER_PASSWORD};value={env:SAFE_VALUE}",
    env: { KILO_SERVER_PASSWORD: "secret", SAFE_VALUE: "allowed" },
  })
  expect(result).toBe("password=;value=allowed")
})

test.skipIf(process.platform !== "linux")("does not substitute process environment files", async () => {
  await expect(
    ConfigVariable.substitute({
      ...source,
      text: "{file:/proc/self/environ}",
    }),
  ).rejects.toThrow('bad file reference: "{file:/proc/self/environ}"')
})

test.skipIf(process.platform !== "linux")("does not substitute an environment file through a symlink", async () => {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), "kilo-config-variable-"))
  const link = path.join(dir, "value")
  await fs.symlink("/proc/self/environ", link)
  try {
    await expect(ConfigVariable.substitute({ ...source, text: `{file:${link}}` })).rejects.toThrow("bad file reference")
  } finally {
    await fs.rm(dir, { recursive: true, force: true })
  }
})
