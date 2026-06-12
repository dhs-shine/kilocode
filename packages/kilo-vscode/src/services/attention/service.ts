import * as vscode from "vscode"
import type { KiloConnectionService } from "../cli-backend/connection-service"
import { AttentionTracker, toAttentionSignal, type AttentionKind, type AttentionNotice } from "./attention"
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

export function previewSound(kind: AttentionSetting, value: string) {
  const id = resolveSoundID(value, fallback(kind))
  if (id) void playSound(id)
}

export class AttentionService implements vscode.Disposable {
  private readonly tracker = new AttentionTracker()
  private readonly unsubscribeEvent: () => void
  private readonly unsubscribeState: () => void

  constructor(connection: KiloConnectionService) {
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
    this.tracker.dispose()
  }

  private notify(notice: AttentionNotice) {
    const key = setting(notice.kind)
    const sounds = vscode.workspace.getConfiguration("kilo-code.new.sounds")
    if (!sounds.get<boolean>(`${key}Enabled`, false)) return
    const value = sounds.get<string>(key, "system")
    const id = resolveSoundID(value, notice.kind)
    if (id) void playSound(id)
  }
}
