#!/usr/bin/env bun
import { fileURLToPath } from "url"

const dir = fileURLToPath(new URL("..", import.meta.url))
process.chdir(dir)

import { $ } from "bun"
import path from "path"

import { createClient } from "@hey-api/openapi-ts"

// kilocode_change start - prevent runtime-dependent generated SDK ordering
const root = path.resolve(dir, "../../..")
const pkg = await Bun.file(path.join(root, "package.json")).text()
const version = pkg.match(/"packageManager":\s*"bun@([^"]+)"/)?.[1]
if (!version) throw new Error("Root packageManager must specify bun@<version>")
if (Bun.version !== version) throw new Error(`SDK generation requires Bun ${version}, but is running Bun ${Bun.version}`)
// kilocode_change end

const opencode = path.resolve(dir, "../../opencode")

await $`bun dev generate > ${dir}/openapi.json`.cwd(opencode)

await createClient({
  input: "./openapi.json",
  output: {
    path: "./src/v2/gen",
    tsConfigPath: path.join(dir, "tsconfig.json"),
    clean: true,
  },
  plugins: [
    {
      name: "@hey-api/typescript",
      exportFromIndex: false,
    },
    {
      name: "@hey-api/sdk",
      instance: "KiloClient",
      exportFromIndex: false,
      auth: false,
      paramsStructure: "flat",
    },
    {
      name: "@hey-api/client-fetch",
      exportFromIndex: false,
      baseUrl: "http://localhost:4096",
    },
  ],
})

await $`bun prettier --write src/gen`
await $`bun prettier --write src/v2`
await $`rm -rf dist tsconfig.tsbuildinfo`
await $`bun tsc`
await $`rm openapi.json`
