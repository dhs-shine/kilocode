import { describe, expect, it, vi } from "vitest"
import * as vscode from "vscode"
import { EditHistoryTracker } from "../editHistoryTracker"

vi.mock("vscode", () => {
  const opens: Array<(doc: unknown) => void> = []
  return {
    workspace: {
      textDocuments: [],
      asRelativePath: (uri: { fsPath: string }) => uri.fsPath.replace("/workspace/", ""),
      onDidOpenTextDocument: (cb: (doc: unknown) => void) => {
        opens.push(cb)
        return { dispose: vi.fn() }
      },
      onDidChangeTextDocument: () => ({ dispose: vi.fn() }),
      onDidCloseTextDocument: () => ({ dispose: vi.fn() }),
      open: (doc: unknown) => opens.forEach((cb) => cb(doc)),
    },
  }
})

type Doc = vscode.TextDocument & { setText(text: string): void }

function doc(path: string, initial: string): Doc {
  const state = { text: initial }
  return {
    uri: { fsPath: path, scheme: "file" },
    getText: () => state.text,
    setText: (text: string) => {
      state.text = text
    },
  } as unknown as Doc
}

describe("EditHistoryTracker", () => {
  it("retains chronological edits across files for Mercury context", () => {
    const tracker = new EditHistoryTracker()
    const a = doc("/workspace/a.ts", "const a = 1\n")
    const b = doc("/workspace/b.ts", "const b = 1\n")
    const open = (vscode.workspace as unknown as { open(doc: vscode.TextDocument): void }).open

    open(a)
    open(b)
    a.setText("const a = 2\n")
    tracker.flush(a)
    b.setText("const b = 2\n")
    tracker.flush(b)

    const diffs = tracker.getRecentDiffs()
    expect(diffs).toHaveLength(2)
    expect(diffs[0]).toContain("a.ts")
    expect(diffs[0]).toContain("+const a = 2")
    expect(diffs[1]).toContain("b.ts")
    expect(diffs[1]).toContain("+const b = 2")

    tracker.dispose()
  })
})
