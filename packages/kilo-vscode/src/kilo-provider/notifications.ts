import * as vscode from "vscode"

export async function resetReadNotifications(
  context: vscode.ExtensionContext | undefined,
  refresh: () => Promise<void>,
): Promise<void> {
  await context?.globalState.update("kilo.dismissedNotificationIds", undefined)
  await refresh()
  vscode.window.showInformationMessage("Read notifications have been reset.")
}
