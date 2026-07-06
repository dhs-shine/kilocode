import fs from "node:fs/promises"
import { realpathSync } from "node:fs"
import path from "node:path"

export namespace ConfigVariableGuard {
  export type FileScope = {
    root: string
    source: string
  }

  const secret = new Set(["KILO_SERVER_PASSWORD", "KILO_SERVER_USERNAME"])

  export function env(name: string) {
    return !secret.has(name.toUpperCase())
  }

  function inside(root: string, file: string) {
    const rel = path.relative(root, file)
    return rel === "" || (!rel.startsWith("..") && !path.isAbsolute(rel))
  }

  function check(file: string, token: string, scope?: FileScope) {
    if (!scope) return
    const root = realpathSync.native(scope.root)
    if (inside(root, file)) return
    throw new Error(`blocked file reference outside project config scope: "${token}"`)
  }

  export async function read(filePath: string, scope?: FileScope & { token?: string }) {
    const file = await fs.open(filePath, "r")
    try {
      // Resolve and validate the file the fd actually points at. On Linux /proc/self/fd pins the fd; on other
      // platforms we realpath the path. Either way the subsequent read is done through the same open fd
      // (file.readFile), never by re-opening the path, so the validated inode is the one we read (no TOCTOU race).
      const target = process.platform === "linux" ? `/proc/self/fd/${file.fd}` : filePath
      const resolved = realpathSync.native(target)
      check(resolved, scope?.token ?? "{file:...}", scope)
      if (/^\/proc\/.*\/environ$/.test(resolved)) throw new Error("blocked process environment reference")
      return await file.readFile("utf-8")
    } finally {
      await file.close()
    }
  }
}
