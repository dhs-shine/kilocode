import { describe, expect, it, mock } from "bun:test"
import * as vscode from "vscode"
import { NotebookAdapter } from "../../src/services/notebook/adapter"
import { normalizeOutputs, normalizeSource } from "../../src/services/notebook/output"
import { NotebookError, resolveNotebookPath } from "../../src/services/notebook/path"
import type { NotebookAdapterDeps, NotebookCellInput } from "../../src/services/notebook/types"

function uri(path: string): vscode.Uri {
  return { scheme: "file", fsPath: path, path, toString: () => `file://${path}` } as vscode.Uri
}

function cell(source = "print('hi')", kind = vscode.NotebookCellKind.Code): vscode.NotebookCell {
  return {
    kind,
    document: { getText: () => source, languageId: kind === vscode.NotebookCellKind.Code ? "python" : "markdown" },
    outputs: [],
    executionSummary: undefined,
  } as unknown as vscode.NotebookCell
}

function notebook(cells: vscode.NotebookCell[], version = 1): vscode.NotebookDocument {
  return {
    uri: uri("/repo/book.ipynb"),
    version,
    isClosed: false,
    cellCount: cells.length,
    getCells: () => cells,
    cellAt: (index: number) => cells[index]!,
  } as unknown as vscode.NotebookDocument
}

function harness(document: vscode.NotebookDocument) {
  const changes = new Set<(event: vscode.NotebookDocumentChangeEvent) => void>()
  const closes = new Set<(document: vscode.NotebookDocument) => void>()
  const calls = { open: 0, apply: 0, command: 0, commandArgs: [] as unknown[], edit: undefined as unknown }
  const deps: NotebookAdapterDeps = {
    documents: () => [document],
    open: async () => {
      calls.open++
      return document
    },
    apply: async () => {
      calls.apply++
      Object.assign(document, { version: document.version + 1 })
      return true
    },
    execute: async (...args) => {
      calls.command++
      calls.commandArgs = args
    },
    change: (listener) => {
      changes.add(listener)
      return { dispose: () => changes.delete(listener) }
    },
    close: (listener) => {
      closes.add(listener)
      return { dispose: () => closes.delete(listener) }
    },
    uri,
    edit: (_uri, edits) => {
      calls.edit = edits
      return { edits } as unknown as vscode.WorkspaceEdit
    },
    insert: (index, cells) => ({ type: "insert", index, cells }) as unknown as vscode.NotebookEdit,
    replace: (index, cells) => ({ type: "replace", index, cells }) as unknown as vscode.NotebookEdit,
    delete: (index) => ({ type: "delete", index }) as unknown as vscode.NotebookEdit,
    cell: (input: NotebookCellInput) => ({ input }) as unknown as vscode.NotebookCellData,
  }
  return { deps, changes, closes, calls }
}

const paths = { realpath: async (value: string) => value }
const access = { validateAccess: mock(() => true) }

function adapter(document: vscode.NotebookDocument, deps = harness(document)) {
  return { adapter: new NotebookAdapter(access, { deps: deps.deps, paths, timeout: 50 }), ...deps }
}

describe("notebook path security", () => {
  it("rejects traversal and symlink escapes before access checks", async () => {
    const guard = { validateAccess: mock(() => true) }
    await expect(resolveNotebookPath("/repo", "../secret.ipynb", guard, paths)).rejects.toMatchObject({
      code: "invalid_path",
    })
    await expect(
      resolveNotebookPath("/repo", "linked.ipynb", guard, {
        realpath: async (value) => (value.endsWith("linked.ipynb") ? "/outside/secret.ipynb" : value),
      }),
    ).rejects.toMatchObject({ code: "invalid_path" })
    expect(guard.validateAccess).not.toHaveBeenCalled()
  })

  it("enforces ignore access on the canonical target", async () => {
    const guard = { validateAccess: mock(() => false) }
    await expect(resolveNotebookPath("/repo", "book.ipynb", guard, paths)).rejects.toMatchObject({
      code: "invalid_path",
    })
    expect(guard.validateAccess).toHaveBeenCalledWith("/repo/book.ipynb")
  })
})

describe("notebook normalization", () => {
  it("bounds UTF-8 source and text outputs and omits rich bodies", () => {
    expect(normalizeSource("abcdef", 3)).toEqual({ text: "abc", bytes: 6, truncated: true })
    expect(normalizeSource("a€b", 3)).toEqual({ text: "a", bytes: 5, truncated: true })
    const normalized = normalizeOutputs(
      [
        {
          items: [
            { mime: "text/plain", data: new TextEncoder().encode("abcdef") },
            { mime: "image/png", data: new Uint8Array(40) },
          ],
        } as vscode.NotebookCellOutput,
      ],
      3,
    )
    expect(normalized).toEqual({
      outputs: [
        { mime: "text/plain", text: "abc", truncated: true },
        { mime: "image/png", omitted: true },
      ],
      truncated: true,
      bytes: 3,
    })
  })

  it("extracts and bounds standard notebook errors", () => {
    const data = new TextEncoder().encode(
      JSON.stringify({ name: "N".repeat(600), message: "M".repeat(11_000), stack: "trace" }),
    )
    const normalized = normalizeOutputs([
      { items: [{ mime: "application/vnd.code.notebook.error", data }] } as vscode.NotebookCellOutput,
    ])
    expect(normalized.outputs[0]).toMatchObject({ stack: "trace" })
    expect(normalized.outputs[0]?.name).toHaveLength(500)
    expect(normalized.outputs[0]?.message).toHaveLength(10_000)
  })
})

describe("notebook adapter", () => {
  it("prefers a dirty open document and reads normalized cells", async () => {
    const document = notebook([cell("unsaved"), cell("# title", vscode.NotebookCellKind.Markup)], 7)
    const ctx = adapter(document)
    const result = await ctx.adapter.read({ directory: "/repo", path: "book.ipynb" })
    expect(ctx.calls.open).toBe(0)
    expect(result).toMatchObject({
      path: "book.ipynb",
      version: 7,
      cells: [
        { index: 0, kind: "code", language: "python", source: "unsaved" },
        { index: 1, kind: "markdown", language: "markdown", source: "# title" },
      ],
    })
  })

  it("opens notebooks in the background when not already open", async () => {
    const document = notebook([cell()])
    const ctx = harness(document)
    ctx.deps.documents = () => []
    const core = new NotebookAdapter(access, { deps: ctx.deps, paths })
    await core.read({ directory: "/repo", path: "book.ipynb" })
    expect(ctx.calls.open).toBe(1)
  })

  it("constructs insert, replace, and delete edits and rejects stale versions", async () => {
    const document = notebook([cell()], 3)
    const ctx = adapter(document)
    await expect(
      ctx.adapter.edit({
        directory: "/repo",
        path: "book.ipynb",
        index: 0,
        version: 2,
        edit: { action: "replace", kind: "code", language: "python", source: "next" },
      }),
    ).rejects.toMatchObject({ code: "stale_version" })
    await ctx.adapter.edit({
      directory: "/repo",
      path: "book.ipynb",
      index: 0,
      version: 3,
      edit: { action: "replace", kind: "code", language: "python", source: "next" },
    })
    expect(ctx.calls.edit).toEqual([
      { type: "replace", index: 0, cells: [{ input: { kind: "code", language: "python", source: "next" } }] },
    ])
  })

  it("correlates a newly completed execution and cleans up listeners", async () => {
    const target = cell()
    const document = notebook([target], 4)
    const ctx = adapter(document)
    ctx.deps.execute = async (...args) => {
      ctx.calls.command++
      ctx.calls.commandArgs = args
      Object.assign(target, {
        outputs: [{ items: [{ mime: "text/plain", data: new TextEncoder().encode("done") }] }],
        executionSummary: { success: true, executionOrder: 2, timing: { startTime: 10, endTime: 20 } },
      })
      for (const listener of ctx.changes) {
        listener({
          notebook: document,
          contentChanges: [],
          cellChanges: [{ cell: target, executionSummary: target.executionSummary }],
        } as unknown as vscode.NotebookDocumentChangeEvent)
      }
    }
    const result = await ctx.adapter.execute({
      directory: "/repo",
      path: "book.ipynb",
      index: 0,
      version: 4,
    })
    expect(result).toMatchObject({ operation: "execute", index: 0, status: "success", outputs: [{ text: "done" }] })
    expect(ctx.calls.commandArgs).toEqual([
      "notebook.cell.execute",
      { ranges: [{ start: 0, end: 1 }], document: document.uri },
    ])
    expect(ctx.changes.size).toBe(0)
    expect(ctx.closes.size).toBe(0)
  })

  it("fails closed when execution never starts", async () => {
    const document = notebook([cell()])
    const ctx = adapter(document)
    await expect(
      ctx.adapter.execute({
        directory: "/repo",
        path: "book.ipynb",
        index: 0,
        version: 1,
        timeout: 5,
      }),
    ).rejects.toMatchObject({ code: "no_kernel" })
    expect(ctx.calls.commandArgs).toEqual([
      "notebook.cell.cancelExecution",
      { ranges: [{ start: 0, end: 1 }], document: document.uri },
    ])
    expect(ctx.changes.size).toBe(0)
    expect(ctx.closes.size).toBe(0)
  })

  it("cancels execution and disposes listeners", async () => {
    const document = notebook([cell()])
    const ctx = adapter(document)
    const controller = new AbortController()
    const pending = ctx.adapter.execute({
      directory: "/repo",
      path: "book.ipynb",
      index: 0,
      version: 1,
      signal: controller.signal,
    })
    controller.abort()
    await expect(pending).rejects.toEqual(
      new NotebookError("cancelled", "Notebook execution cancellation was requested"),
    )
    expect(ctx.changes.size).toBe(0)
    expect(ctx.closes.size).toBe(0)
  })
})
