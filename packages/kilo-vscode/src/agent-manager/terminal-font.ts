/**
 * Helpers to read and watch the user's integrated-terminal font settings.
 *
 * VS Code's integrated terminal mirrors editor font settings when terminal
 * font settings are unset. We replicate that same logic here so the Agent
 * Manager xterm instances look identical to VS Code's own terminal.
 */

import * as vscode from "vscode"

export interface TerminalFont {
  fontFamily: string
  fontSize: number
  fontWeight?: string
  fontWeightBold?: string
  lineHeight?: number
  letterSpacing?: number
}

const FALLBACK = "Menlo, Monaco, 'Courier New', monospace"

/** Resolve the user's integrated-terminal font, mirroring VS Code's own
 *  fallback to the editor font when the terminal font is unset. */
export function readTerminalFont(): TerminalFont {
  const term = vscode.workspace.getConfiguration("terminal.integrated")
  const editor = vscode.workspace.getConfiguration("editor")
  const family = term.get<string>("fontFamily")?.trim() || editor.get<string>("fontFamily")?.trim() || FALLBACK
  const size = term.get<number>("fontSize") || editor.get<number>("fontSize") || 13
  return {
    fontFamily: family,
    fontSize: size,
    fontWeight: term.get<string>("fontWeight") || undefined,
    fontWeightBold: term.get<string>("fontWeightBold") || undefined,
    lineHeight: term.get<number>("lineHeight") || undefined,
    letterSpacing: term.get<number>("letterSpacing") || undefined,
  }
}

/** True when a config change touches any terminal/editor font setting. */
export function affectsTerminalFont(e: vscode.ConfigurationChangeEvent): boolean {
  return (
    e.affectsConfiguration("terminal.integrated.fontFamily") ||
    e.affectsConfiguration("terminal.integrated.fontSize") ||
    e.affectsConfiguration("terminal.integrated.fontWeight") ||
    e.affectsConfiguration("terminal.integrated.fontWeightBold") ||
    e.affectsConfiguration("terminal.integrated.lineHeight") ||
    e.affectsConfiguration("terminal.integrated.letterSpacing") ||
    e.affectsConfiguration("editor.fontFamily") ||
    e.affectsConfiguration("editor.fontSize")
  )
}

/** Subscribe to terminal-font config changes. Returns a cleanup function. */
export function watchTerminalFont(callback: (font: TerminalFont) => void): () => void {
  const sub = vscode.workspace.onDidChangeConfiguration((e) => {
    if (affectsTerminalFont(e)) callback(readTerminalFont())
  })
  return () => sub.dispose()
}
