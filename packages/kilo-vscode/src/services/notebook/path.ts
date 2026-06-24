import fs from "node:fs/promises"
import path from "node:path"
import type { NotebookAccess } from "./types"

const WINDOWS_ABSOLUTE = /^[a-zA-Z]:[/\\]/

export class NotebookError extends Error {
  constructor(
    public readonly code: string,
    message: string,
  ) {
    super(message)
    this.name = "NotebookError"
  }
}

export interface NotebookPathDeps {
  realpath(path: string): Promise<string>
}

const defaults: NotebookPathDeps = { realpath: fs.realpath }

function contained(root: string, target: string): boolean {
  const relative = path.relative(root, target)
  return relative === "" || (!relative.startsWith(`..${path.sep}`) && relative !== ".." && !path.isAbsolute(relative))
}

export async function resolveNotebookPath(
  directory: string,
  relative: string,
  access: NotebookAccess,
  deps: NotebookPathDeps = defaults,
): Promise<string> {
  if (!relative || relative.length > 4_096 || path.isAbsolute(relative) || WINDOWS_ABSOLUTE.test(relative)) {
    throw new NotebookError("invalid_path", "Notebook path must be workspace-relative")
  }

  const root = await deps.realpath(path.resolve(directory))
  const candidate = path.resolve(root, relative)
  if (!contained(root, candidate)) {
    throw new NotebookError("invalid_path", "Notebook path is outside the request directory")
  }

  const target = await deps.realpath(candidate).catch((error: unknown) => {
    const detail = error instanceof Error ? error.message : String(error)
    throw new NotebookError("not_found", `Cannot resolve notebook: ${detail}`)
  })
  if (!contained(root, target)) {
    throw new NotebookError("invalid_path", "Notebook resolves outside the request directory")
  }
  if (!(await access.validateAccess(target))) {
    throw new NotebookError("invalid_path", "Notebook is excluded by workspace access rules")
  }
  return target
}
