import * as fs from "fs"
import * as path from "path"

const dir = "tree-sitter"
const runtime = "tree-sitter.wasm"

function paths(file: string) {
  if (/^[a-z]:[\\/]/i.test(file) || file.includes("\\")) return path.win32
  return path
}

export function treeSitterDirForBinary(file: string): string {
  const p = paths(file)
  return p.join(p.dirname(file), dir)
}

export function treeSitterDirForExtension(root: string): string {
  return paths(root).join(root, "bin", dir)
}

export function resolveTreeSitterEnv(root: string): Record<string, string> {
  return { KILO_TREE_SITTER_WASM_DIR: treeSitterDirForExtension(root) }
}

export function hasTreeSitterResources(file: string): boolean {
  return fs.existsSync(path.join(treeSitterDirForBinary(file), runtime))
}

export async function copyTreeSitterResources(source: string, target: string): Promise<void> {
  const from = treeSitterDirForBinary(source)
  const to = treeSitterDirForBinary(target)

  if (!fs.existsSync(path.join(from, runtime))) {
    throw new Error(`CLI tree-sitter resources not found at ${from}`)
  }

  await fs.promises.rm(to, { recursive: true, force: true })
  await fs.promises.cp(from, to, { recursive: true })
}

export async function copySandboxResources(source: string, target: string): Promise<void> {
  const from = path.dirname(source)
  const to = path.dirname(target)
  const helper = path.join(to, "bwrap")
  const destination = path.join(to, "licenses", "bubblewrap")
  await fs.promises.rm(helper, { force: true })
  await fs.promises.rm(destination, { recursive: true, force: true })

  const bwrap = path.join(from, "bwrap")
  if (!fs.existsSync(bwrap)) return
  await fs.promises.copyFile(bwrap, helper)
  await fs.promises.chmod(helper, 0o755)

  const licenses = path.join(from, "licenses", "bubblewrap")
  if (!fs.existsSync(licenses)) return
  await fs.promises.cp(licenses, destination, { recursive: true })
}
