#!/usr/bin/env bun

/**
 * CI test runner for the JetBrains plugin.
 *
 * Runs ./gradlew test --continue so all modules run even when some fail,
 * then collects per-module JUnit XML results into .artifacts/unit/junit.xml
 * so mikepenz/action-junit-report can find them at the standard path.
 *
 * Always exits 0 — test failures are reported via the JUnit uploader,
 * not by failing the CI job itself.
 */

import { $ } from "bun"
import { join } from "node:path"
import { mkdirSync, readdirSync, readFileSync, writeFileSync, existsSync } from "node:fs"

const root = join(import.meta.dir, "..")

await $`./gradlew test --continue`.cwd(root).nothrow()

const modules = [".", "shared", "frontend", "backend"]
const suites: string[] = []

for (const mod of modules) {
  const dir = join(root, mod === "." ? "" : mod, "build", "test-results", "test")
  if (!existsSync(dir)) continue
  for (const f of readdirSync(dir)) {
    if (!f.endsWith(".xml")) continue
    suites.push(readFileSync(join(dir, f), "utf8"))
  }
}

const out = join(root, ".artifacts", "unit", "junit.xml")
mkdirSync(join(root, ".artifacts", "unit"), { recursive: true })
writeFileSync(out, `<?xml version="1.0" encoding="UTF-8"?>\n<testsuites>\n${suites.join("\n")}\n</testsuites>\n`)

console.log(`[jetbrains-test] collected ${suites.length} suite(s) -> ${out}`)
