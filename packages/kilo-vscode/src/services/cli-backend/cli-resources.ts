import * as fs from "fs"
import * as path from "path"

const dir = "tree-sitter"
const runtime = "tree-sitter.wasm"
const sandboxWorker = "sandbox-mutation-worker.js"

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

export function sandboxWorkerForBinary(file: string): string {
  const p = paths(file)
  return p.join(p.dirname(file), sandboxWorker)
}

export function hasSandboxWorker(file: string): boolean {
  return fs.existsSync(sandboxWorkerForBinary(file))
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

export async function copySandboxWorker(source: string, target: string): Promise<void> {
  const from = sandboxWorkerForBinary(source)
  const to = sandboxWorkerForBinary(target)
  if (!fs.existsSync(from)) throw new Error(`CLI sandbox mutation worker not found at ${from}`)
  await fs.promises.copyFile(from, to)
}
