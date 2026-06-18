import * as path from "node:path"
import * as vscode from "vscode"
import { listSessions, scanTaskStore, type ScanDiagnostic, type SessionCatalog } from "../legacy-migration/task-store"

const ROOTS = [
  "roovscode.roo-cline",
  "roovscode.roo-code",
  "rooveterinaryinc.roo-cline",
  "rooveterinaryinc.roo-code",
  "rooveterinaryinc.roo-code-nightly",
]

export interface RooImportSource {
  catalog: SessionCatalog
  sessions: ReturnType<typeof listSessions>
  diagnostics: ScanDiagnostic[]
}

/** Scans every known Roo storage root and keeps the first deterministic copy of duplicate task IDs. */
export async function detectRooCodeSessions(context: vscode.ExtensionContext): Promise<RooImportSource | null> {
  const parent = path.dirname(context.globalStorageUri.fsPath)
  const catalog: SessionCatalog = new Map()
  const diagnostics: ScanDiagnostic[] = []

  for (const root of ROOTS) {
    const dir = path.join(parent, root, "tasks")
    const scan = await scanTaskStore(dir, [], { namespace: "roo", mode: "discover" })
    diagnostics.push(...scan.diagnostics)
    for (const [id, entry] of [...scan.catalog].sort(([a], [b]) => a.localeCompare(b))) {
      if (!catalog.has(id)) catalog.set(id, entry)
    }
  }

  for (const diagnostic of diagnostics) {
    console.warn(`[Kilo New] Roo import skipped ${diagnostic.reason} task ${diagnostic.id} in ${diagnostic.dir}`)
  }

  const sessions = listSessions(catalog)
  return sessions.length ? { catalog, sessions, diagnostics } : null
}
