#!/usr/bin/env bun
// kilocode_change - new file

import path from "path"
import semver from "semver"
import { parseArgs } from "util"

type Bump = "major" | "minor" | "patch"

export type Release = {
  tag_name: string
  name?: string | null
  body?: string | null
  draft?: boolean
  prerelease?: boolean
}

type Opts = {
  from: string
  to: string
  repo: string
  root: string
  packages: string[]
  bump: Bump
  dry: boolean
  force: boolean
  prerelease: boolean
}

const usage = `
Usage: bun script/opencode-changesets.ts <from> <to> [options]

Creates one changeset per upstream opencode release in the semver range (from, to].

Options:
      --from <version>          Starting opencode version, exclusive
      --to <version>            Ending opencode version, inclusive
      --repo <owner/repo>       GitHub repository (default: anomalyco/opencode)
      --package <name>          Changeset package (repeatable; defaults to @kilocode/cli and kilo-code)
      --bump <type>             Changeset bump type: major, minor, patch (default: patch)
      --dry-run                 Print changesets without writing files
      --force                   Overwrite existing generated changeset files
      --include-prerelease      Include prerelease GitHub releases
  -h, --help                    Show this help message

Examples:
  bun script/opencode-changesets.ts 1.17.0 1.17.7
  bun script/opencode-changesets.ts --from v1.16.0 --to v1.17.7 --dry-run
`

function clean(input: string) {
  const raw = input.trim().replace(/^v/, "")
  const version = semver.valid(raw)
  if (!version) throw new Error(`Invalid semver version: ${input}`)
  return version
}

function tag(input: string) {
  return `v${clean(input)}`
}

function slug(input: string) {
  return `opencode-${tag(input).replace(/[^a-zA-Z0-9]+/g, "-").replace(/^-|-$/g, "").toLowerCase()}.md`
}

function packages(input: string[], bump: Bump) {
  return input.map((item) => `"${item}": ${bump}`).join("\n")
}

function body(release: Release) {
  const text = release.body?.replace(/\r\n?/g, "\n").trim()
  if (text) return text

  const name = release.name?.trim()
  if (name && name !== release.tag_name) return name

  return `Integrate upstream opencode ${release.tag_name}.`
}

function isRelease(input: unknown): input is Release {
  return Boolean(input && typeof input === "object" && "tag_name" in input && typeof input.tag_name === "string")
}

export function select(releases: Release[], from: string, to: string, prerelease = false) {
  const base = clean(from)
  const head = clean(to)
  if (semver.gt(base, head) || base === head) throw new Error(`Expected from version to be lower than to version`)

  const seen = new Set<string>()
  return releases
    .filter((release) => !release.draft)
    .filter((release) => prerelease || !release.prerelease)
    .map((release) => ({ release, version: semver.valid(release.tag_name.replace(/^v/, "")) }))
    .filter((item): item is { release: Release; version: string } => Boolean(item.version))
    .filter((item) => {
      if (seen.has(item.version)) return false
      seen.add(item.version)
      return semver.gt(item.version, base) && semver.lte(item.version, head)
    })
    .sort((a, b) => semver.compare(a.version, b.version))
    .map((item) => ({ ...item.release, tag_name: tag(item.version) }))
}

export function changeset(release: Release, opts: Pick<Opts, "packages" | "bump">) {
  return `---\n${packages(opts.packages, opts.bump)}\n---\n\nIntegrate upstream opencode ${release.tag_name} release notes.\n\n${body(release)}\n`
}

async function all(repo: string) {
  const list: Release[] = []
  const auth = process.env.GH_TOKEN ?? process.env.GITHUB_TOKEN

  for (let page = 1; ; page++) {
    const res = await fetch(`https://api.github.com/repos/${repo}/releases?per_page=100&page=${page}`, {
      headers: {
        Accept: "application/vnd.github+json",
        ...(auth ? { Authorization: `Bearer ${auth}` } : {}),
      },
    })

    if (!res.ok) throw new Error(`GitHub releases request failed for ${repo}: ${res.status} ${await res.text()}`)

    const json: unknown = await res.json()
    if (!Array.isArray(json) || !json.every(isRelease)) throw new Error(`GitHub returned invalid release data`)

    const batch = json
    list.push(...batch)
    if (batch.length < 100) return list
  }
}

function opts() {
  const parsed = parseArgs({
    args: Bun.argv.slice(2),
    options: {
      from: { type: "string" },
      to: { type: "string" },
      repo: { type: "string", default: "anomalyco/opencode" },
      package: { type: "string", multiple: true },
      bump: { type: "string", default: "patch" },
      "dry-run": { type: "boolean", default: false },
      force: { type: "boolean", default: false },
      "include-prerelease": { type: "boolean", default: false },
      help: { type: "boolean", short: "h", default: false },
    },
    allowPositionals: true,
  })

  if (parsed.values.help) {
    process.stdout.write(usage)
    process.exit(0)
  }

  const from = parsed.values.from ?? parsed.positionals[0]
  const to = parsed.values.to ?? parsed.positionals[1]
  if (!from || !to) throw new Error("Expected from and to opencode versions")

  const bump = parsed.values.bump
  if (bump !== "major" && bump !== "minor" && bump !== "patch") throw new Error(`Invalid bump type: ${bump}`)

  return {
    from,
    to,
    repo: parsed.values.repo,
    root: path.resolve(import.meta.dir, ".."),
    packages: parsed.values.package?.length ? parsed.values.package : ["@kilocode/cli", "kilo-code"],
    bump,
    dry: parsed.values["dry-run"],
    force: parsed.values.force,
    prerelease: parsed.values["include-prerelease"],
  } satisfies Opts
}

async function write(releases: Release[], opts: Opts) {
  const dir = path.join(opts.root, ".changeset")

  for (const release of releases) {
    const file = path.join(dir, slug(release.tag_name))
    const text = changeset(release, opts)

    if (opts.dry) {
      process.stdout.write(`--- ${path.relative(opts.root, file)} ---\n${text}\n`)
      continue
    }

    if (!opts.force && (await Bun.file(file).exists())) throw new Error(`Changeset already exists: ${file}`)
    await Bun.write(file, text)
    process.stdout.write(`Wrote ${path.relative(opts.root, file)}\n`)
  }
}

export async function run(opts: Opts) {
  const releases = select(await all(opts.repo), opts.from, opts.to, opts.prerelease)
  if (releases.length === 0) throw new Error(`No opencode releases found in range (${opts.from}, ${opts.to}]`)

  await write(releases, opts)
}

if (import.meta.main) {
  await run(opts()).catch((err) => {
    process.stderr.write(`${err instanceof Error ? err.message : String(err)}\n`)
    process.exit(1)
  })
}
