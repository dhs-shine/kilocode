import { describe, expect, it, vi } from "vitest"
import * as vscode from "vscode"
import type { KiloConnectionService } from "../../../cli-backend"
import { NextEditInlineCompletionProvider } from "../NextEditInlineCompletionProvider"
import type { NextEditSuggestionManager } from "../NextEditSuggestionManager"

vi.mock("vscode", () => {
  class Position {
    constructor(
      public line: number,
      public character: number,
    ) {}
  }
  class Range {
    constructor(
      public start: Position,
      public end: Position,
    ) {}
  }
  return {
    Position,
    Range,
    InlineCompletionItem: class {},
    workspace: {
      textDocuments: [],
      onDidOpenTextDocument: () => ({ dispose: vi.fn() }),
      onDidChangeTextDocument: () => ({ dispose: vi.fn() }),
      onDidCloseTextDocument: () => ({ dispose: vi.fn() }),
    },
    window: {
      createOutputChannel: () => ({ appendLine: vi.fn(), dispose: vi.fn() }),
    },
  }
})

type Subject = {
  toCompletionItems(
    document: vscode.TextDocument,
    position: vscode.Position,
    suggestion: {
      replacement: string
      editableRegionStartLine: number
      editableRegionEndLine: number
      latencyMs: number
    },
  ): vscode.InlineCompletionItem[] | undefined
}

describe("NextEditInlineCompletionProvider", () => {
  it("stashes same-line rewrites before the cursor for decorated acceptance", () => {
    const mgr = { clear: vi.fn(), setPending: vi.fn() }
    const provider = new NextEditInlineCompletionProvider({
      connectionService: {} as KiloConnectionService,
      suggestionManager: mgr as unknown as NextEditSuggestionManager,
    })
    const text = "const oldName = make()"
    const document = {
      lineCount: 1,
      lineAt: () => ({ text, range: { end: new vscode.Position(0, text.length) } }),
      getText: () => text,
    } as unknown as vscode.TextDocument

    const out = (provider as unknown as Subject).toCompletionItems(document, new vscode.Position(0, 13), {
      replacement: "const newName = make()",
      editableRegionStartLine: 0,
      editableRegionEndLine: 0,
      latencyMs: 1,
    })

    expect(out).toBeUndefined()
    expect(mgr.setPending).toHaveBeenCalledWith(
      expect.objectContaining({ kind: "replace", replacement: "const newName = make()" }),
    )
    provider.dispose()
  })
})
