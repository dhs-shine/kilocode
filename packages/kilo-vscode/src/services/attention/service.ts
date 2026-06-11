import * as vscode from "vscode"
import type { KiloConnectionService } from "../cli-backend/connection-service"
import { AttentionTracker, delivery, toAttentionSignal, type AttentionKind, type AttentionNotice } from "./attention"
import { playSound, resolveSoundID } from "./sound"

export type AttentionSetting = "agent" | "permissions" | "errors"

function setting(kind: AttentionKind): AttentionSetting {
  if (kind === "error") return "errors"
  if (kind === "question" || kind === "permission") return "permissions"
  return "agent"
}

function fallback(kind: AttentionSetting): AttentionKind {
  if (kind === "errors") return "error"
  if (kind === "permissions") return "permission"
  return "done"
}

function clean(value: string | undefined) {
  return (value ?? "Kilo session")
    .replace(/[\u0000-\u001F\u007F-\u009F]/g, "")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 80)
}

export function previewSound(kind: AttentionSetting, value: string) {
  const id = resolveSoundID(value, fallback(kind))
  if (id) void playSound(id)
}

export class AttentionService implements vscode.Disposable {
  private readonly tracker = new AttentionTracker()
  private readonly unsubscribeEvent: () => void
  private readonly unsubscribeState: () => void
  private readonly focus: vscode.Disposable
  private appFocused = vscode.window.state.focused

  constructor(private readonly connection: KiloConnectionService) {
    this.focus = vscode.window.onDidChangeWindowState((state) => {
      this.appFocused = state.focused
    })
    this.unsubscribeEvent = connection.onEvent((event) => {
      const signal = toAttentionSignal(event)
      if (!signal) return
      const notice = this.tracker.handle(signal)
      if (notice) this.notify(notice)
    })
    this.unsubscribeState = connection.onStateChange((state) => {
      if (state === "error" || state === "disconnected") this.tracker.reset()
    })
  }

  dispose() {
    this.unsubscribeEvent()
    this.unsubscribeState()
    this.focus.dispose()
    this.tracker.dispose()
  }

  private notify(notice: AttentionNotice) {
    const key = setting(notice.kind)
    const notifications = vscode.workspace.getConfiguration("kilo-code.new.notifications")
    const sounds = vscode.workspace.getConfiguration("kilo-code.new.sounds")
    const value = sounds.get<string>(key, "system")
    const id = resolveSoundID(value, notice.kind)
    const result = delivery({
      appFocused: this.appFocused,
      sessionFocused: this.connection.isSessionFocused(notice.sessionID),
      subagent: notice.subagent,
      notifications: notifications.get<boolean>(key, true),
      sound: id !== undefined,
      playWhenFocused: sounds.get<boolean>("playWhenFocused", false),
    })

    if (result.sound && id) void playSound(id)
    if (!result.notification) return

    const message = `${clean(notice.title)}: ${notice.message}`
    if (notice.kind === "error") {
      void vscode.window.showErrorMessage(message)
      return
    }
    void vscode.window.showInformationMessage(message)
  }
}
