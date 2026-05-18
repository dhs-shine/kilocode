#!/usr/bin/env bun
// kilocode_change - new file

/**
 * Greps tracked files for forbidden strings that must not appear in the repo.
 *
 * Currently enforced:
 *   - opncd.ai/s/  -- legacy upstream OpenCode share URL pattern. Kilo shares
 *                     go through a different host/path; this string sneaking
 *                     back in usually means a hardcoded upstream URL.
 */

import { spawnSync } from "node:child_process"
import path from "node:path"

const ROOT = path.resolve(import.meta.dir, "..")
const SELF = path.relative(ROOT, import.meta.path).replaceAll("\\", "/")

const forbidden = [{ pattern: "opncd.ai/s/", reason: "legacy upstream share URL pattern" }]

const ls = spawnSync("git", ["ls-files", "-z"], { cwd: ROOT, encoding: "buffer" })
if (ls.status !== 0) {
  console.error(ls.stderr?.toString().trim() || "git ls-files failed")
  process.exit(1)
}

const files = ls.stdout
  .toString("utf8")
  .split("\0")
  .filter(Boolean)
  .filter((f) => f !== SELF)

const hits: string[] = []
for (const file of files) {
  const buf = Bun.file(path.join(ROOT, file))
  if (!(await buf.exists())) continue
  // Skip binary-ish files: read as text and skip if it contains a NUL byte.
  const text = await buf.text().catch(() => null)
  if (text === null) continue
  if (text.includes("\0")) continue
  for (const f of forbidden) {
    let idx = 0
    while (true) {
      const at = text.indexOf(f.pattern, idx)
      if (at === -1) break
      const line = text.slice(0, at).split("\n").length
      hits.push(`${file}:${line}: ${f.pattern} (${f.reason})`)
      idx = at + f.pattern.length
    }
  }
}

if (hits.length === 0) {
  console.log(`check-forbidden-strings: ${files.length} file(s) checked, no forbidden strings found.`)
  process.exit(0)
}

console.error("Found forbidden strings:")
for (const h of hits) console.error(`  ${h}`)
process.exit(1)
