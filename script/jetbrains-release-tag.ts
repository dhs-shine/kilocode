#!/usr/bin/env bun
// kilocode_change - new file

import { $ } from "bun"
import semver from "semver"
import { parseArgs } from "util"

const repo = process.env.GH_REPO ?? process.env.GITHUB_REPOSITORY ?? "Kilo-Org/kilocode"
const { values } = parseArgs({
  args: Bun.argv.slice(2),
  options: {
    pr: { type: "string" },
    dry: { type: "boolean", default: false },
    help: { type: "boolean", short: "h", default: false },
  },
})

if (values.help) {
  console.log(`
Usage: bun script/jetbrains-release-tag.ts --pr <number> [--dry]
`)
  process.exit(0)
}

const pr = values.pr ?? process.env.PR_NUMBER
if (!pr) throw new Error("--pr is required")

type Pull = {
  body: string
  headRefName: string
  isCrossRepository: boolean
  labels: { name: string }[]
  mergeCommit: { oid: string } | null
}

const data = (await $`gh pr view ${pr} --repo ${repo} --json body,headRefName,isCrossRepository,labels,mergeCommit`.json()) as Pull
const labels = new Set(data.labels.map((item) => item.name))
if (!labels.has("jetbrains-release")) throw new Error("PR is missing jetbrains-release label")
if (!data.headRefName.startsWith("jetbrains/release/")) throw new Error("PR head branch must start with jetbrains/release/")
if (data.isCrossRepository) throw new Error("JetBrains release PR must come from this repository")
if (!data.mergeCommit?.oid) throw new Error("PR has no merge commit")

const ver = marker(data.body, "JetBrains-Version") ?? data.headRefName.replace(/^jetbrains\/release\/v/, "")
const tag = marker(data.body, "JetBrains-Tag") ?? `jetbrains/v${ver}`
if (!semver.valid(ver)) throw new Error(`Invalid JetBrains version: ${ver}`)
if (tag !== `jetbrains/v${ver}`) throw new Error(`Tag ${tag} does not match version ${ver}`)
if (!/^jetbrains\/v\d+\.\d+\.\d+(-rc\.\d+)?$/.test(tag)) throw new Error(`Invalid JetBrains tag: ${tag}`)

const pkg = await Bun.file("packages/kilo-jetbrains/package.json").json()
if (pkg.version !== ver) throw new Error(`packages/kilo-jetbrains/package.json version is ${pkg.version}, expected ${ver}`)

const changelog = await Bun.file("packages/kilo-jetbrains/CHANGELOG.md").text()
if (!changelog.includes(`## [${ver}]`)) throw new Error(`CHANGELOG.md is missing section for ${ver}`)

await $`git fetch origin --tags`
const existing = await $`git rev-parse -q --verify ${`refs/tags/${tag}`}`.nothrow()
if (existing.exitCode === 0) {
  const sha = (await $`git rev-list -n 1 ${tag}`.text()).trim()
  if (sha === data.mergeCommit.oid) {
    console.log(`${tag} already exists at ${sha}`)
    process.exit(0)
  }
  throw new Error(`${tag} already exists at ${sha}, expected ${data.mergeCommit.oid}`)
}

console.log(`Creating ${tag} at ${data.mergeCommit.oid}`)
if (values.dry) {
  console.log("Dry run complete. No tag was created.")
  process.exit(0)
}

await $`git tag ${tag} ${data.mergeCommit.oid}`
await $`git push origin ${tag}`

function marker(body: string, key: string) {
  const line = body.split(/\r?\n/).find((item) => item.startsWith(`${key}:`))
  return line?.slice(key.length + 1).trim()
}
