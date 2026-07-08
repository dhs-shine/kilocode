#!/usr/bin/env bun

import { $ } from "bun"
import semver from "semver"
import { parseArgs } from "util"

const repo = process.env.GH_REPO ?? process.env.GITHUB_REPOSITORY ?? "Kilo-Org/kilocode"
const asset = [
  "kilo-darwin-arm64.zip",
  "kilo-darwin-x64.zip",
  "kilo-linux-arm64.tar.gz",
  "kilo-linux-x64.tar.gz",
  "kilo-windows-arm64.zip",
  "kilo-windows-x64.zip",
]

const { values } = parseArgs({
  args: Bun.argv.slice(2),
  options: {
    help: { type: "boolean", short: "h", default: false },
  },
})

if (values.help) {
  console.log(`
Usage: bun .kilo/skills/release-jetbrains/script/check-pin.ts

Checks the CLI pin that a JetBrains release would lock. Prepare tags origin/main,
so this reads packages/kilo-jetbrains/package.json from origin/main and compares
it with the latest published Kilo CLI release plus the local worktree pin.

Exit codes:
  0  Pin is release-ready.
  2  Pin drift, repo CLI mode, or missing CLI assets require maintainer review.
`)
  process.exit(0)
}

await $`git fetch origin main --tags`.quiet()

const pinMain = JSON.parse(await $`git show origin/main:packages/kilo-jetbrains/package.json`.text()).version as string
const pinLocal = (await Bun.file("packages/kilo-jetbrains/package.json").json()).version as string
const propsMain = await $`git show origin/main:packages/kilo-jetbrains/gradle.properties`.text()
const propsLocal = await Bun.file("packages/kilo-jetbrains/gradle.properties").text()
const pinnedMain = pinned(propsMain)
const pinnedLocal = pinned(propsLocal)
const latestCli = await latest()
const prevJetbrainsCli = await previous()
const missingAssets = await missing(pinMain)
const assetsOk = missingAssets.length === 0
const drift = (() => {
  if (!pinnedMain) return "repo-mode-on-main"
  if (!pinnedLocal) return "repo-mode-local"
  if (pinLocal !== pinMain) return "worktree-behind-main"
  if (!assetsOk) return "assets-missing"
  if (latestCli && semver.lt(pinMain, latestCli)) return "behind"
  return "up-to-date"
})()

console.log(JSON.stringify({
  pinMain,
  pinLocal,
  latestCli,
  prevJetbrainsCli,
  pinnedMain,
  pinnedLocal,
  assetsOk,
  missingAssets,
  drift,
}, null, 2))

if (drift !== "up-to-date") process.exit(2)

async function latest() {
  const list = (await $`gh release list --repo ${repo} --limit 100 --json tagName,isDraft,isPrerelease`.json()) as {
    tagName: string
    isDraft: boolean
    isPrerelease: boolean
  }[]
  return list
    .filter((item) => /^v\d+\.\d+\.\d+$/.test(item.tagName) && !item.isDraft && !item.isPrerelease)
    .map((item) => item.tagName.slice(1))
    .sort(semver.rcompare)[0] ?? null
}

async function previous() {
  const text = await $`git tag --list ${"jetbrains/v*"}`.text()
  const tag = text
    .split(/\r?\n/)
    .map((item) => item.trim())
    .filter(Boolean)
    .map((tag) => ({ tag, version: tag.replace(/^jetbrains\/v/, "") }))
    .filter((item) => semver.valid(item.version))
    .sort((a, b) => semver.rcompare(a.version, b.version))[0]?.tag
  if (!tag) return null
  const res = await $`git show ${tag}:packages/kilo-jetbrains/package.json`.nothrow().text()
  if (!res.trim()) return null
  return JSON.parse(res).version as string
}

async function missing(version: string) {
  const res = await $`gh release view ${`v${version}`} --repo ${repo} --json assets --jq ${".assets[].name"}`.quiet().nothrow()
  if (res.exitCode !== 0) return asset
  const names = res.stdout.toString().split(/\r?\n/).map((item) => item.trim()).filter(Boolean)
  return asset.filter((item) => !names.includes(item))
}

function pinned(text: string) {
  const line = text.split(/\r?\n/).find((item) => item.startsWith("kilo.cli.pinned="))
  const value = line?.split("=", 2)[1]?.trim().toLowerCase()
  return value == null || value === "true"
}
