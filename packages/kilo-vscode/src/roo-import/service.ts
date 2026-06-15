/**
 * roo-import/service.ts
 *
 * Detects and reads sessions from a Roo Code extension installation.
 * Uses the same data format as the legacy Kilo Code migration — Roo Code
 * stores tasks under `<globalStorage>/<extensionId>/tasks/<id>/api_conversation_history.json`,
 * identical to the format the existing migration pipeline already handles.
 */

import * as path from "node:path"
import * as vscode from "vscode"
import type { MigrationSessionInfo } from "../legacy-migration/legacy-types"
import type { LegacyHistoryItem } from "../legacy-migration/sessions/lib/legacy-types"

/** Known Roo Code VS Code extension IDs, most common first. */
const ROO_CODE_EXTENSION_IDS = ["roovscode.roo-cline", "roovscode.roo-code"]

export interface RooImportSource {
  /** Absolute path to the Roo Code tasks directory. */
  dir: string
  sessions: MigrationSessionInfo[]
  items: LegacyHistoryItem[]
}

/**
 * Discovers the Roo Code tasks directory by looking for known Roo Code extension
 * IDs as siblings of the current extension's global storage directory.
 * Returns `null` when no Roo Code installation is detected.
 */
export async function detectRooCodeSessions(
  context: vscode.ExtensionContext,
): Promise<RooImportSource | null> {
  const storageParent = path.dirname(context.globalStorageUri.fsPath)

  for (const id of ROO_CODE_EXTENSION_IDS) {
    const dir = path.join(storageParent, id, "tasks")
    const result = await scanTasksDir(dir)
    if (result) return result
  }

  return null
}

async function scanTasksDir(dir: string): Promise<RooImportSource | null> {
  const uri = vscode.Uri.file(dir)
  let entries: [string, vscode.FileType][]
  try {
    entries = await vscode.workspace.fs.readDirectory(uri)
  } catch {
    return null
  }

  const sessions: MigrationSessionInfo[] = []
  const items: LegacyHistoryItem[] = []

  for (const [name, type] of entries) {
    if (type !== vscode.FileType.Directory) continue
    const histFile = vscode.Uri.file(path.join(dir, name, "api_conversation_history.json"))
    const exists = await vscode.workspace.fs.stat(histFile).then(
      () => true,
      () => false,
    )
    if (!exists) continue

    const ts = parseTaskTimestamp(name)
    const title = await extractTitleFromHistory(path.join(dir, name, "api_conversation_history.json"), name)

    sessions.push({
      id: name,
      title,
      directory: "",
      time: ts,
    })

    items.push({
      id: name,
      ts,
      task: title,
      workspace: "",
    })
  }

  if (sessions.length === 0) return null

  // Sort newest first (matches legacy migration UI order)
  sessions.sort((a, b) => b.time - a.time)
  items.sort((a, b) => (b.ts ?? 0) - (a.ts ?? 0))

  return { dir, sessions, items }
}

/**
 * Roo Code task IDs are numeric millisecond timestamps (e.g. `1699023456789`).
 * Returns the parsed value or 0 for non-numeric IDs.
 */
function parseTaskTimestamp(id: string): number {
  const n = Number(id)
  return Number.isFinite(n) && n > 1_000_000_000_000 ? n : 0
}

/**
 * Reads the first user message from `api_conversation_history.json` to derive
 * a human-readable session title, falling back to the task ID.
 */
async function extractTitleFromHistory(file: string, fallback: string): Promise<string> {
  try {
    const bytes = await vscode.workspace.fs.readFile(vscode.Uri.file(file))
    const json = JSON.parse(Buffer.from(bytes).toString("utf8")) as unknown
    if (!Array.isArray(json)) return formatFallbackTitle(fallback)
    for (const msg of json) {
      if (!msg || typeof msg !== "object") continue
      if ((msg as { role?: string }).role !== "user") continue
      const content = (msg as { content?: unknown }).content
      const text = extractText(content)
      if (text) return text.slice(0, 120).replace(/\n/g, " ").trim()
    }
  } catch {
    // fall through to default
  }
  return formatFallbackTitle(fallback)
}

function extractText(content: unknown): string {
  if (typeof content === "string") return content
  if (Array.isArray(content)) {
    for (const block of content) {
      if (block && typeof block === "object" && (block as { type?: string }).type === "text") {
        const t = (block as { text?: unknown }).text
        if (typeof t === "string" && t.trim()) return t
      }
    }
  }
  return ""
}

/**
 * Formats a numeric timestamp ID as a readable date, or returns the raw ID
 * for non-numeric task IDs.
 */
function formatFallbackTitle(id: string): string {
  const ts = parseTaskTimestamp(id)
  return ts > 0 ? new Date(ts).toLocaleString() : id
}
