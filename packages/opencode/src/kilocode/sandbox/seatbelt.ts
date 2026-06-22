import { base } from "./seatbelt-base"
import type { Scope, Root } from "./scope"

const SEATBELT = "/usr/bin/sandbox-exec"

// Only .git is hard-protected from writes. .kilo is left writable because
// ConfigProtection already gates .kilo/ edits with an ask prompt.
const META = [".git"]

function escapeRegex(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")
}

function protectedMetaRegex(root: string, name: string): string {
  let r = root
  while (r.length > 1 && r.endsWith("/")) r = r.slice(0, -1)
  const er = escapeRegex(r)
  const en = escapeRegex(name)
  if (r === "/") return `^/${en}(/.*)?$`
  return `^${er}/${en}(/.*)?$`
}

interface Param {
  key: string
  value: string
}

function buildWritePolicy(roots: Root[]): { policy: string; params: Param[] } {
  const parts: string[] = []
  const params: Param[] = []

  roots.forEach((root, i) => {
    const rp = `WRITABLE_ROOT_${i}`
    params.push({ key: rp, value: root.path })

    const require: string[] = [`(subpath (param "${rp}"))`]

    root.readonlySubpaths.forEach((sub, j) => {
      const ep = `${rp}_EXCL_${j}`
      params.push({ key: ep, value: sub })
      require.push(`(require-not (literal (param "${ep}")))`)
      require.push(`(require-not (subpath (param "${ep}")))`)
    })

    for (const name of META) {
      if (root.readonlySubpaths.some((s) => s === `${root.path}/${name}`)) continue
      const re = protectedMetaRegex(root.path, name).replace(/"/g, '\\"')
      require.push(`(require-not (regex #"${re}"))`)
    }

    parts.push(`(require-all ${require.join(" ")} )`)
  })

  if (parts.length === 0) return { policy: "", params }
  return { policy: `(allow file-write*\n${parts.join(" ")}\n)`, params }
}

export interface Command {
  command: string
  args: string[]
}

export function available(): boolean {
  try {
    const fs = require("fs")
    return fs.existsSync(SEATBELT)
  } catch {
    return false
  }
}

export function generate(scope: Scope, command: string, args: string[]): Command {
  const write = buildWritePolicy(scope.writableRoots)

  const policy = [base, "; reads are not confined by the file-level sandbox\n(allow file-read*)", write.policy].join(
    "\n",
  )

  const sbArgs: string[] = ["-p", policy]
  for (const p of write.params) sbArgs.push(`-D${p.key}=${p.value}`)
  sbArgs.push("--", command, ...args)

  return { command: SEATBELT, args: sbArgs }
}
