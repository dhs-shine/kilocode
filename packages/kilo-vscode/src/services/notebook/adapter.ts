import path from "node:path"
import * as vscode from "vscode"
import { normalizeOutputs, normalizeSource } from "./output"
import { NotebookError, resolveNotebookPath, type NotebookPathDeps } from "./path"
import {
  NOTEBOOK_LIMITS,
  type NotebookAccess,
  type NotebookAdapterDeps,
  type NotebookCell,
  type NotebookEditRequest,
  type NotebookEditResult,
  type NotebookExecuteRequest,
  type NotebookExecuteResult,
  type NotebookExecution,
  type NotebookReadRequest,
  type NotebookReadResult,
} from "./types"

export interface NotebookAdapterOptions {
  deps?: NotebookAdapterDeps
  paths?: NotebookPathDeps
  timeout?: number
}

function execution(summary: vscode.NotebookCellExecutionSummary | undefined): NotebookExecution | undefined {
  if (!summary) {
    return undefined
  }
  return {
    order: summary.executionOrder,
    success: summary.success,
    started: summary.timing?.startTime,
    ended: summary.timing?.endTime,
  }
}

function changed(
  base: vscode.NotebookCellExecutionSummary | undefined,
  summary: vscode.NotebookCellExecutionSummary | undefined,
): boolean {
  if (!summary) return false
  return (
    summary.executionOrder !== base?.executionOrder ||
    summary.success !== base?.success ||
    summary.timing?.startTime !== base?.timing?.startTime ||
    summary.timing?.endTime !== base?.timing?.endTime
  )
}

function defaults(): NotebookAdapterDeps {
  return {
    documents: () => vscode.workspace.notebookDocuments,
    open: (uri) => Promise.resolve(vscode.workspace.openNotebookDocument(uri)),
    apply: (edit) => Promise.resolve(vscode.workspace.applyEdit(edit)),
    execute: (command, ...args) => Promise.resolve(vscode.commands.executeCommand(command, ...args)),
    change: (listener) => vscode.workspace.onDidChangeNotebookDocument(listener),
    close: (listener) => vscode.workspace.onDidCloseNotebookDocument(listener),
    uri: vscode.Uri.file,
    edit: (uri, edits) => {
      const edit = new vscode.WorkspaceEdit()
      edit.set(uri, edits)
      return edit
    },
    insert: (index, cells) => vscode.NotebookEdit.insertCells(index, cells),
    replace: (index, cells) => vscode.NotebookEdit.replaceCells(new vscode.NotebookRange(index, index + 1), cells),
    delete: (index) => vscode.NotebookEdit.deleteCells(new vscode.NotebookRange(index, index + 1)),
    cell: (input) =>
      new vscode.NotebookCellData(
        input.kind === "code" ? vscode.NotebookCellKind.Code : vscode.NotebookCellKind.Markup,
        input.source,
        input.language ?? (input.kind === "code" ? "plaintext" : "markdown"),
      ),
  }
}

export class NotebookAdapter {
  private readonly deps: NotebookAdapterDeps
  private readonly timeout: number

  constructor(
    private readonly access: NotebookAccess,
    private readonly options: NotebookAdapterOptions = {},
  ) {
    this.deps = options.deps ?? defaults()
    this.timeout = options.timeout ?? 120_000
  }

  private async document(
    directory: string,
    relative: string,
  ): Promise<{ document: vscode.NotebookDocument; path: string }> {
    const target = await resolveNotebookPath(directory, relative, this.access, this.options.paths)
    const open = this.deps.documents().find((document) => path.resolve(document.uri.fsPath) === path.resolve(target))
    const document = open ?? (await this.deps.open(this.deps.uri(target)))
    if (document.isClosed) {
      throw new NotebookError("closed", "Notebook document is closed")
    }
    return { document, path: target }
  }

  async read(request: NotebookReadRequest): Promise<NotebookReadResult> {
    const loaded = await this.document(request.directory, request.path)
    const cells: NotebookCell[] = []
    const budget = { sources: 0, outputs: 0 }
    const flags = { sources: false, outputs: false }

    const source = loaded.document.getCells()
    if (source.length > 2_000) {
      flags.sources = true
    }
    for (const [index, cell] of source.slice(0, 2_000).entries()) {
      const source = normalizeSource(
        cell.document.getText(),
        Math.max(0, Math.min(NOTEBOOK_LIMITS.source, NOTEBOOK_LIMITS.sources - budget.sources)),
      )
      budget.sources += Math.min(source.bytes, NOTEBOOK_LIMITS.source, NOTEBOOK_LIMITS.sources - budget.sources)
      flags.sources ||= source.truncated === true
      const value: NotebookCell = {
        index,
        kind: cell.kind === vscode.NotebookCellKind.Code ? "code" : "markdown",
        language: cell.document.languageId.slice(0, 200),
        source: source.text,
        execution: execution(cell.executionSummary),
      }
      if (request.includeOutputs) {
        const normalized = normalizeOutputs(
          cell.outputs,
          Math.max(0, Math.min(NOTEBOOK_LIMITS.output, NOTEBOOK_LIMITS.outputs - budget.outputs)),
        )
        value.outputs = normalized.outputs
        budget.outputs += normalized.bytes
        if (normalized.truncated) {
          flags.outputs = true
        }
      }
      cells.push(value)
    }

    return {
      operation: "read",
      path: request.path,
      version: loaded.document.version,
      cells,
      ...(flags.sources || flags.outputs ? { truncated: true } : {}),
    }
  }

  async edit(request: NotebookEditRequest): Promise<NotebookEditResult> {
    const loaded = await this.document(request.directory, request.path)
    this.version(loaded.document, request.version)
    const count = loaded.document.cellCount
    const max = request.edit.action === "insert" ? count : count - 1
    if (!Number.isInteger(request.index) || request.index < 0 || request.index > max) {
      throw new NotebookError("invalid_cell", `Cell index ${request.index} is out of range`)
    }

    const edits = (() => {
      if (request.edit.action === "delete") {
        return [this.deps.delete(request.index)]
      }
      const cell = this.deps.cell({
        kind: request.edit.kind,
        language: request.edit.language,
        source: request.edit.source,
      })
      if (request.edit.action === "insert") {
        return [this.deps.insert(request.index, [cell])]
      }
      return [this.deps.replace(request.index, [cell])]
    })()
    this.version(loaded.document, request.version)
    if (!(await this.deps.apply(this.deps.edit(loaded.document.uri, edits)))) {
      throw new NotebookError("unsupported", "VS Code rejected the notebook edit")
    }
    return {
      operation: "edit",
      path: request.path,
      version: loaded.document.version,
      index: request.index,
      action: request.edit.action,
    }
  }

  async execute(request: NotebookExecuteRequest): Promise<NotebookExecuteResult> {
    const loaded = await this.document(request.directory, request.path)
    this.version(loaded.document, request.version)
    if (!Number.isInteger(request.index) || request.index < 0 || request.index >= loaded.document.cellCount) {
      throw new NotebookError("invalid_cell", `Cell index ${request.index} is out of range`)
    }
    const cell = loaded.document.cellAt(request.index)
    if (cell.kind !== vscode.NotebookCellKind.Code) {
      throw new NotebookError("invalid_cell", `Cell ${request.index} is not a code cell`)
    }

    const result = this.wait(loaded.document, cell, request)
    void this.deps
      .execute("notebook.cell.execute", {
        ranges: [{ start: request.index, end: request.index + 1 }],
        document: loaded.document.uri,
      })
      .catch((error: unknown) => {
        const detail = error instanceof Error ? error.message : String(error)
        result.reject(new NotebookError("execution_failed", `Notebook execution could not start: ${detail}`))
      })
    return result.promise
  }

  private wait(document: vscode.NotebookDocument, cell: vscode.NotebookCell, request: NotebookExecuteRequest) {
    const state: {
      done: boolean
      timer?: ReturnType<typeof setTimeout>
      startup?: ReturnType<typeof setTimeout>
    } = { done: false }
    const base = cell.executionSummary
    const disposables: vscode.Disposable[] = []
    const cleanup = () => {
      if (state.done) {
        return false
      }
      state.done = true
      if (state.timer) {
        clearTimeout(state.timer)
      }
      if (state.startup) {
        clearTimeout(state.startup)
      }
      for (const disposable of disposables) {
        disposable.dispose()
      }
      request.signal?.removeEventListener("abort", abort)
      return true
    }
    const holder: {
      resolve?: (value: NotebookExecuteResult) => void
      reject?: (error: Error) => void
    } = {}
    const promise = new Promise<NotebookExecuteResult>((resolve, reject) => {
      holder.resolve = resolve
      holder.reject = reject
    })
    const reject = (error: Error) => {
      if (cleanup()) {
        holder.reject?.(error)
      }
    }
    const resolve = () => {
      if (!cleanup()) {
        return
      }
      const normalized = normalizeOutputs(cell.outputs)
      holder.resolve?.({
        operation: "execute",
        path: request.path,
        version: document.version,
        index: request.index,
        status: cell.executionSummary?.success === false ? "error" : "success",
        outputs: normalized.outputs,
        ...(normalized.truncated ? { truncated: true } : {}),
      })
    }
    const stop = () =>
      void this.deps.execute("notebook.cell.cancelExecution", {
        ranges: [{ start: request.index, end: request.index + 1 }],
        document: document.uri,
      })
    const abort = () => {
      stop()
      reject(new NotebookError("cancelled", "Notebook execution cancellation was requested"))
    }

    disposables.push(
      this.deps.change((event) => {
        if (event.notebook !== document) {
          return
        }
        if (document.isClosed || document.cellCount <= request.index || document.cellAt(request.index) !== cell) {
          reject(new NotebookError("stale_version", "Notebook cell changed during execution"))
          return
        }
        const change = event.cellChanges.find((item) => item.cell === cell)
        if (!change) {
          return
        }
        const summary = change.executionSummary
        if (!summary || !changed(base, summary)) return
        if (state.startup) clearTimeout(state.startup)
        if (summary.success !== undefined || summary.timing?.endTime !== undefined) resolve()
      }),
      this.deps.close((closed) => {
        if (closed === document) {
          reject(new NotebookError("closed", "Notebook closed during execution"))
        }
      }),
    )
    const timeout = request.timeout ?? this.timeout
    state.startup = setTimeout(
      () => {
        stop()
        reject(
          new NotebookError(
            "no_kernel",
            "Notebook execution did not start; open the notebook in VS Code and select a kernel",
          ),
        )
      },
      Math.min(timeout, 10_000),
    )
    state.timer = setTimeout(() => {
      stop()
      reject(new NotebookError("timeout", "Notebook execution timed out"))
    }, timeout)
    if (request.signal?.aborted) {
      abort()
    } else {
      request.signal?.addEventListener("abort", abort, { once: true })
    }
    return { promise, reject }
  }

  private version(document: vscode.NotebookDocument, expected: number): void {
    if (document.version !== expected) {
      throw new NotebookError(
        "stale_version",
        `Notebook version changed (expected ${expected}, current ${document.version})`,
      )
    }
  }
}
