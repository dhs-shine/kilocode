import fs from "node:fs/promises"
import os from "node:os"
import path from "node:path"
import { expect, test } from "bun:test"
import { ConfigVariable } from "@/config/variable"
import { InvalidError } from "@/config/error"

const source = { type: "virtual" as const, source: "test", dir: process.cwd() }
const trusted = { ...source, trusted: true }

test("rejects file references in untrusted (project) config", async () => {
  await expect(
    ConfigVariable.substitute({ ...source, text: "apiKey={file:/etc/passwd}" }),
  ).rejects.toBeInstanceOf(InvalidError)
})

test("rejects file references when trusted is omitted (secure by default)", async () => {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), "kilo-config-variable-untrusted-"))
  const file = path.join(dir, "secret")
  await fs.writeFile(file, "top-secret")
  try {
    await expect(ConfigVariable.substitute({ ...source, text: `{file:${file}}` })).rejects.toBeInstanceOf(
      InvalidError,
    )
  } finally {
    await fs.rm(dir, { recursive: true, force: true })
  }
})

test("rejects environment references in untrusted (project) config", async () => {
  await expect(
    ConfigVariable.substitute({ ...source, text: "value={env:SAFE_VALUE}", env: { SAFE_VALUE: "allowed" } }),
  ).rejects.toBeInstanceOf(InvalidError)
})

test("leaves untrusted text without references untouched", async () => {
  expect(await ConfigVariable.substitute({ ...source, text: "plain value" })).toBe("plain value")
})

test("ignores commented-out references in untrusted config", async () => {
  const text = ["// {file:/etc/passwd}", "// {env:SAFE_VALUE}"].join("\n")
  expect(await ConfigVariable.substitute({ ...source, text })).toBe(text)
})

test("rejects server credential environment substitutions", async () => {
  await expect(
    ConfigVariable.substitute({
      ...trusted,
      text: "password={env:KILO_SERVER_PASSWORD}",
      env: { KILO_SERVER_PASSWORD: "secret" },
    }),
  ).rejects.toBeInstanceOf(InvalidError)
})

test("continues to substitute ordinary environment variables when trusted", async () => {
  const result = await ConfigVariable.substitute({
    ...trusted,
    text: "value={env:SAFE_VALUE}",
    env: { SAFE_VALUE: "allowed" },
  })
  expect(result).toBe("value=allowed")
})

test("reads ordinary file substitutions on every platform when trusted", async () => {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), "kilo-config-variable-file-"))
  const file = path.join(dir, "value")
  await fs.writeFile(file, "allowed")
  try {
    expect(await ConfigVariable.substitute({ ...trusted, text: `{file:${file}}` })).toBe("allowed")
  } finally {
    await fs.rm(dir, { recursive: true, force: true })
  }
})

test.skipIf(process.platform !== "linux")("does not substitute process environment files", async () => {
  await expect(
    ConfigVariable.substitute({
      ...trusted,
      text: "{file:/proc/self/environ}",
    }),
  ).rejects.toBeInstanceOf(InvalidError)
})

test.skipIf(process.platform !== "linux")("does not substitute an environment file through a symlink", async () => {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), "kilo-config-variable-"))
  const link = path.join(dir, "value")
  await fs.symlink("/proc/self/environ", link)
  try {
    await expect(ConfigVariable.substitute({ ...trusted, text: `{file:${link}}` })).rejects.toBeInstanceOf(
      InvalidError,
    )
  } finally {
    await fs.rm(dir, { recursive: true, force: true })
  }
})
