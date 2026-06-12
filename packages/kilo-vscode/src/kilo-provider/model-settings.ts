import * as vscode from "vscode"

const SECTION = "kilo-code.new.models"

export function buildModelSettingsMessage() {
  const config = vscode.workspace.getConfiguration(SECTION)
  return {
    type: "modelSettingsLoaded" as const,
    settings: {
      hidePromptTraining: config.get<boolean>("hidePromptTraining", false),
    },
  }
}

export function watchModelConfig(post: (msg: unknown) => void, refresh: () => void): vscode.Disposable {
  return vscode.workspace.onDidChangeConfiguration((event) => {
    if (!event.affectsConfiguration(SECTION)) return
    post(buildModelSettingsMessage())
    refresh()
  })
}

export function validModelSetting(key: string, value: unknown) {
  return key === "hidePromptTraining" && typeof value === "boolean"
}
