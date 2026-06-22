import { Effect, Layer } from "effect"
import { AppFileSystem } from "@opencode-ai/core/filesystem"
import { Config } from "@/config/config"
import { InstanceState } from "@/effect/instance-state"
import { Filesystem } from "@/util/filesystem"
import { available } from "./seatbelt"
import { resolve, withExternalDirs, type Scope } from "./scope"

function longestExistingAncestor(p: string): string {
  let dir = p
  let rest = ""
  const fs = require("fs")
  while (dir && dir !== "/" && !fs.existsSync(dir)) {
    const idx = dir.lastIndexOf("/")
    rest = `${dir.slice(idx)}${rest}`
    dir = dir.slice(0, idx)
  }
  if (!dir) return Filesystem.resolve(p)
  return Filesystem.resolve(dir) + rest
}

function isUnder(child: string, parent: string): boolean {
  if (child === parent) return true
  if (parent === "/") return true
  return child.startsWith(`${parent}/`)
}

function writable(scope: Scope, filepath: string): boolean {
  const target = longestExistingAncestor(filepath)
  for (const root of scope.writableRoots) {
    if (!isUnder(target, root.path)) continue
    for (const sub of root.readonlySubpaths) {
      if (isUnder(target, sub) || target === sub) return false
    }
    return true
  }
  return false
}

function externalDirAllows(cfg: Config.Info): string[] {
  const rule = cfg.permission?.external_directory
  if (!rule) return []
  if (typeof rule === "string") return rule === "allow" ? ["*"] : []
  const patterns: string[] = []
  for (const [pattern, action] of Object.entries(rule)) {
    if (action === "allow") patterns.push(pattern)
  }
  return patterns
}

function wrap(fs: AppFileSystem.Interface, config: Config.Interface): AppFileSystem.Interface {
  const guard = (path: string) =>
    Effect.gen(function* () {
      const cfg = yield* config.get()
      if (!cfg.experimental?.sandbox) return
      if (process.platform !== "darwin" || !available()) return
      const ctx = yield* InstanceState.context
      const scope = withExternalDirs(resolve(ctx), externalDirAllows(cfg))
      if (!writable(scope, path)) {
        throw new Error(
          `Sandbox blocked write to ${path}: outside the allowed project and Kilo directories. ` +
            `To allow this directory, approve it as an external directory or disable the sandbox.`,
        )
      }
    })

  return {
    ...fs,
    writeFile: (path, data) => Effect.gen(function* () { yield* guard(path); return yield* fs.writeFile(path, data) }),
    writeFileString: (path, data) => Effect.gen(function* () { yield* guard(path); return yield* fs.writeFileString(path, data) }),
    writeJson: (path, data, mode) => Effect.gen(function* () { yield* guard(path); return yield* fs.writeJson(path, data, mode) }),
    ensureDir: (path) => Effect.gen(function* () { yield* guard(path); return yield* fs.ensureDir(path) }),
    writeWithDirs: (path, content, mode) => Effect.gen(function* () { yield* guard(path); return yield* fs.writeWithDirs(path, content, mode) }),
    makeDirectory: (path) => Effect.gen(function* () { yield* guard(path); return yield* fs.makeDirectory(path) }),
    rename: (oldPath, newPath) => Effect.gen(function* () { yield* guard(oldPath); yield* guard(newPath); return yield* fs.rename(oldPath, newPath) }),
    copyFile: (oldPath, newPath) => Effect.gen(function* () { yield* guard(newPath); return yield* fs.copyFile(oldPath, newPath) }),
    copy: (oldPath, newPath) => Effect.gen(function* () { yield* guard(newPath); return yield* fs.copy(oldPath, newPath) }),
    remove: (path) => Effect.gen(function* () { yield* guard(path); return yield* fs.remove(path) }),
    chmod: (path, mode) => Effect.gen(function* () { yield* guard(path); return yield* fs.chmod(path, mode) }),
    truncate: (path, size) => Effect.gen(function* () { yield* guard(path); return yield* fs.truncate(path, size) }),
    link: (target, linkPath) => Effect.gen(function* () { yield* guard(linkPath); return yield* fs.link(target, linkPath) }),
    symlink: (target, linkPath) => Effect.gen(function* () { yield* guard(linkPath); return yield* fs.symlink(target, linkPath) }),
  }
}

export const layer = Layer.effect(
  AppFileSystem.Service,
  Effect.gen(function* () {
    const inner = yield* AppFileSystem.Service
    const config = yield* Config.Service
    return AppFileSystem.Service.of(wrap(inner, config))
  }),
)
