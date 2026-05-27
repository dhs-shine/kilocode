#!/usr/bin/env bun
// kilocode_change - new file

import { $ } from "bun"
import semver from "semver"
import { parseArgs } from "util"

const pkgfile = new URL("../packages/kilo-jetbrains/package.json", import.meta.url).pathname
const log = new URL("../packages/kilo-jetbrains/CHANGELOG.md", import.meta.url).pathname
const repo = process.env.GH_REPO ?? process.env.GITHUB_REPOSITORY ?? "Kilo-Org/kilocode"

const { values } = parseArgs({
  args: Bun.argv.slice(2),
  options: {
    kind: { type: "string" },
    version: { type: "string" },
    "from-tag": { type: "string" },
    dry: { type: "boolean", default: false },
    help: { type: "boolean", short: "h", default: false },
  },
})

if (values.help) {
  console.log(`
Usage: bun script/jetbrains-release-pr.ts --kind <rc|stable> --version <version> [--from-tag <tag>] [--dry]

Examples:
  bun script/jetbrains-release-pr.ts --kind rc --version 7.3.13-rc.1
  bun script/jetbrains-release-pr.ts --kind stable --version 7.3.13
`)
  process.exit(0)
}

const kind = values.kind
const ver = values.version
const dry = values.dry ?? false

if (kind !== "rc" && kind !== "stable") throw new Error("--kind must be rc or stable")
if (!ver) throw new Error("--version is required")
if (kind === "rc" && !/^\d+\.\d+\.\d+-rc\.\d+$/.test(ver)) throw new Error("RC versions must match x.y.z-rc.n")
if (kind === "stable" && !/^\d+\.\d+\.\d+$/.test(ver)) throw new Error("Stable versions must match x.y.z")
if (!semver.valid(ver)) throw new Error(`Invalid semver: ${ver}`)

await $`git fetch origin main --tags`

const tag = `jetbrains/v${ver}`
const branch = `jetbrains/release/v${ver}`
const from = values["from-tag"] ?? (await base(ver, kind))
const notes = await release(from, tag)
const entry = section(ver, notes)

console.log(`JetBrains ${kind} release PR`)
console.log(`version: ${ver}`)
console.log(`base: ${from}`)
console.log(`tag: ${tag}`)
console.log(`branch: ${branch}`)

if (dry) {
  console.log("\nGenerated changelog entry:\n")
  console.log(entry)
  console.log("\nDry run complete. No branch, commit, push, or PR was created.")
  process.exit(0)
}

await $`git checkout -B ${branch} origin/main`
await writepkg(ver)
await writelog(ver, entry)
await $`git add packages/kilo-jetbrains/package.json packages/kilo-jetbrains/CHANGELOG.md`

const changed = await $`git diff --cached --quiet`.nothrow()
if (changed.exitCode !== 0) await $`git commit -m ${`release(jetbrains): v${ver}`}`

await $`git push --force-with-lease origin ${branch}`

const text = body(ver, kind, from, tag, notes)
const view = await $`gh pr view ${branch} --repo ${repo} --json number --jq .number`.nothrow()
if (view.exitCode === 0 && view.stdout.toString().trim()) {
  const num = view.stdout.toString().trim()
  await $`gh pr edit ${num} --repo ${repo} --title ${`release(jetbrains): v${ver}`} --body ${text}`
  await $`gh pr edit ${num} --repo ${repo} --add-label jetbrains-release --add-label release`.nothrow()
  console.log(`Updated PR #${num}`)
  process.exit(0)
}

const create = await $`gh pr create --repo ${repo} --base main --head ${branch} --title ${`release(jetbrains): v${ver}`} --body ${text}`.text()
await $`gh pr edit ${branch} --repo ${repo} --add-label jetbrains-release --add-label release`.nothrow()
console.log(create.trim())

async function base(ver: string, kind: "rc" | "stable") {
  const text = await $`git tag --list ${"jetbrains/v*"}`.text()
  const tags = text
    .split(/\r?\n/)
    .map((item) => item.trim())
    .filter(Boolean)
    .map((item) => ({ tag: item, ver: item.replace(/^jetbrains\/v/, "") }))
    .filter((item) => semver.valid(item.ver))

  const target = semver.parse(ver)!
  const stable = tags
    .filter((item) => !semver.prerelease(item.ver) && semver.lt(item.ver, ver))
    .sort((a, b) => semver.rcompare(a.ver, b.ver))

  if (kind === "stable") {
    const hit = stable[0]
    if (!hit) throw new Error("No previous stable JetBrains tag found; pass --from-tag")
    return hit.tag
  }

  const rc = tags
    .filter((item) => {
      const parsed = semver.parse(item.ver)
      if (!parsed) return false
      if (parsed.major !== target.major || parsed.minor !== target.minor || parsed.patch !== target.patch) return false
      return Boolean(semver.prerelease(item.ver)) && semver.lt(item.ver, ver)
    })
    .sort((a, b) => semver.rcompare(a.ver, b.ver))

  const hit = rc[0] ?? stable[0]
  if (!hit) throw new Error("No previous JetBrains tag found; pass --from-tag")
  return hit.tag
}

async function release(from: string, tag: string) {
  const res = await $`gh api repos/${repo}/releases/generate-notes --method POST -f tag_name=${tag} -f target_commitish=main -f previous_tag_name=${from} --jq .body`
    .quiet()
    .nothrow()
  if (res.exitCode === 0) return res.stdout.toString().trim()

  const text = await $`git log --format=%s ${from}..origin/main`.text()
  const lines = text
    .split(/\r?\n/)
    .map((item) => item.trim())
    .filter(Boolean)
    .filter((item) => !/^(chore|ci|test|release)(\(|:)/i.test(item))
  return lines.map((item) => `- ${item}`).join("\n") || "- No notable changes."
}

function section(ver: string, notes: string) {
  const date = new Date().toISOString().slice(0, 10)
  const lines = bullets(notes)
  return [`## [${ver}] - ${date}`, "", "### Changed", ...lines, ""].join("\n")
}

function bullets(notes: string) {
  const lines = notes
    .split(/\r?\n/)
    .map((item) => item.trim())
    .filter((item) => item.startsWith("- ") || item.startsWith("* "))
    .map((item) => `- ${item.slice(2).trim()}`)
  return lines.length > 0 ? lines : ["- No notable changes."]
}

async function writepkg(ver: string) {
  const pkg = await Bun.file(pkgfile).json()
  pkg.version = ver
  await Bun.write(pkgfile, `${JSON.stringify(pkg, null, 2)}\n`)
}

async function writelog(ver: string, entry: string) {
  const current = await Bun.file(log).text().catch(() => "# Changelog\n\n## [Unreleased]\n")
  const clean = current.replace(regex(ver), "").replace(/\n{3,}/g, "\n\n")
  const marker = "## [Unreleased]"
  if (!clean.includes(marker)) throw new Error("CHANGELOG.md must contain ## [Unreleased]")
  const next = clean.replace(marker, `${marker}\n\n${entry.trim()}\n`)
  await Bun.write(log, `${next.trim()}\n`)
}

function regex(ver: string) {
  const safe = ver.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")
  return new RegExp(`\\n?## \\[${safe}\\][\\s\\S]*?(?=\\n## \\[|$)`, "m")
}

function body(ver: string, kind: string, from: string, tag: string, notes: string) {
  return `## Summary
- Prepare JetBrains ${kind} release ${ver}.
- Review and edit \`packages/kilo-jetbrains/CHANGELOG.md\` before merging.

JetBrains-Version: ${ver}
JetBrains-Kind: ${kind}
JetBrains-From-Tag: ${from}
JetBrains-Tag: ${tag}

## Generated Notes
${notes || "No notable changes."}
`
}
