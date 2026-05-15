#!/usr/bin/env bun

/**
 * CI test runner for the JetBrains plugin.
 *
 * Runs ./gradlew test --continue so all modules run even when some fail,
 * then collects per-module JUnit XML results into .artifacts/unit/junit.xml
 * so mikepenz/action-junit-report can find them at the standard path.
 *
 * Exits with the Gradle exit code after writing the aggregate report so that
 * test failures fail the CI job.
 */

import { $ } from "bun"
import { join } from "node:path"
import { mkdirSync, readdirSync, readFileSync, writeFileSync, existsSync } from "node:fs"

const root = join(import.meta.dir, "..")
const gradlew = process.platform === "win32" ? "gradlew.bat" : "./gradlew"

const result = await $`${gradlew} test --continue`.cwd(root).nothrow()

const modules = [".", "shared", "frontend", "backend"]
const suites: string[] = []

for (const mod of modules) {
  const dir = join(root, mod === "." ? "" : mod, "build", "test-results", "test")
  if (!existsSync(dir)) continue
  for (const f of readdirSync(dir)) {
    if (!f.endsWith(".xml")) continue
    // Strip leading XML declaration so it does not appear as a nested
    // declaration inside the <testsuites> wrapper, which would produce
    // malformed XML and fail the JUnit report uploader.
    const xml = readFileSync(join(dir, f), "utf8").replace(/^\s*<\?xml[^>]*\?>\s*/u, "")
    suites.push(xml)
  }
}

const out = join(root, ".artifacts", "unit", "junit.xml")
mkdirSync(join(root, ".artifacts", "unit"), { recursive: true })
writeFileSync(out, `<?xml version="1.0" encoding="UTF-8"?>\n<testsuites>\n${suites.join("\n")}\n</testsuites>\n`)

console.log(`[jetbrains-test] collected ${suites.length} suite(s) -> ${out}`)

process.exit(result.exitCode)
