/**
 * roo-import/command.ts
 *
 * VS Code command that imports session history from a Roo Code installation.
 * Reuses the existing `migrate()` function from the legacy migration pipeline —
 * passing the Roo Code tasks directory as an override so no new parsing logic
 * is needed.
 */

import * as vscode from "vscode"
import type { KiloClient } from "@kilocode/sdk/v2/client"
import { migrate } from "../legacy-migration/sessions/migrate"
import type { MigrationSessionSelection } from "../legacy-migration/legacy-types"
import { buildSessionMeta } from "../legacy-migration/migration-session-progress"
import { detectRooCodeSessions } from "./service"

export async function importRooCodeSessionsCommand(
  context: vscode.ExtensionContext,
  getClient: () => KiloClient | null,
): Promise<void> {
  const client = getClient()
  if (!client) {
    void vscode.window.showErrorMessage("Kilo Code is not connected. Please wait for it to start and try again.")
    return
  }

  const source = await vscode.window.withProgress(
    { location: vscode.ProgressLocation.Notification, title: "Scanning for Roo Code sessions…" },
    () => detectRooCodeSessions(context),
  )

  if (!source) {
    void vscode.window.showInformationMessage("No Roo Code sessions found.")
    return
  }

  const { sessions, items, dir } = source
  const picked = await vscode.window.showQuickPick(
    sessions.map((s) => ({
      label: s.title,
      description: s.time > 0 ? new Date(s.time).toLocaleString() : s.id,
      id: s.id,
      picked: true,
    })),
    {
      canPickMany: true,
      title: `Import Roo Code Sessions (${sessions.length} found)`,
      placeHolder: "Select sessions to import",
    },
  )

  if (!picked || picked.length === 0) return

  const selected = picked.map((p) => p.id)

  await vscode.window.withProgress(
    {
      location: vscode.ProgressLocation.Notification,
      title: "Importing Roo Code sessions…",
      cancellable: false,
    },
    async (progress) => {
      let done = 0
      for (const id of selected) {
        const session = sessions.find((s) => s.id === id)
        const item = items.find((i) => i.id === id)
        const selection: MigrationSessionSelection = { id }
        const meta = buildSessionMeta(session, done, selected.length)

        progress.report({
          message: `${done + 1} / ${selected.length}: ${session?.title ?? id}`,
          increment: 100 / selected.length,
        })

        await migrate(selection, context, client, meta, undefined, { dir, item })
        done++
      }
      progress.report({ message: `Done — imported ${done} session(s).`, increment: 0 })
    },
  )

  void vscode.window.showInformationMessage(`Roo Code import complete: ${selected.length} session(s) imported.`)
}
