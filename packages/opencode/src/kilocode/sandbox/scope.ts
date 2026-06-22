import { Global } from "@opencode-ai/core/global"
import { Filesystem } from "@/util/filesystem"
import type { InstanceContext } from "@/project/instance-context"

export interface Root {
  path: string
  readonlySubpaths: string[]
}

export interface Scope {
  writableRoots: Root[]
}

// Only .git is hard-protected from writes. .kilo is intentionally left writable
// because ConfigProtection (kilocode/permission/config-paths.ts) already forces
// an ask prompt for edits to .kilo/ config files — the sandbox should not
// override that softer, user-consent-based gate with a hard block.
const PROTECTED_METADATA_NAMES = [".git"]

function real(p: string): string {
  return Filesystem.resolve(p)
}

function addRoot(writable: Root[], dir: string) {
  const r = real(dir)
  if (!writable.some((w) => w.path === r)) writable.push({ path: r, readonlySubpaths: [] })
}

function protectMeta(root: Root) {
  for (const name of PROTECTED_METADATA_NAMES) {
    const meta = `${root.path}/${name}`
    if (!root.readonlySubpaths.includes(meta)) root.readonlySubpaths.push(meta)
  }
}

export function resolve(ctx: InstanceContext): Scope {
  const writable: Root[] = []

  if (ctx.worktree !== "/") addRoot(writable, ctx.worktree)
  addRoot(writable, ctx.directory)
  for (const s of ctx.project.sandboxes ?? []) addRoot(writable, s)

  const dirs = [
    Global.Path.data,
    Global.Path.cache,
    Global.Path.config,
    Global.Path.state,
    Global.Path.tmp,
    Global.Path.bin,
    Global.Path.log,
    Global.Path.repos,
  ]
  for (const dir of dirs) addRoot(writable, dir)

  for (const root of writable) protectMeta(root)

  return { writableRoots: writable }
}

function expandHome(p: string): string {
  if (p.startsWith("~/")) return real(`${process.env.HOME ?? ""}${p.slice(1)}`)
  if (p === "~") return real(process.env.HOME ?? "")
  return p
}

export function withExternalDirs(scope: Scope, patterns: string[]): Scope {
  const writable = [...scope.writableRoots]
  for (const pattern of patterns) {
    let dir = expandHome(pattern.trim())
    if (dir.endsWith("/*")) dir = dir.slice(0, -2)
    else if (dir.endsWith("/")) dir = dir.slice(0, -1)
    if (!dir || dir === "*" || dir.includes("*")) continue
    let root: Root | undefined = writable.find((w) => w.path === real(dir))
    if (!root) {
      root = { path: real(dir), readonlySubpaths: [] }
      writable.push(root)
    }
    protectMeta(root)
  }
  return { writableRoots: writable }
}
