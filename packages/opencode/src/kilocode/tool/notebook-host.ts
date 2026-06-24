import { Notebook, HostError } from "@/kilocode/notebook/service"
import { Path, type Result } from "@/kilocode/notebook/protocol"
import { NonNegativeInt } from "@opencode-ai/core/schema"
import * as Tool from "@/tool/tool"
import { Effect, Schema } from "effect"

const Source = Schema.String.check(Schema.isMaxLength(200_000))
const Version = NonNegativeInt.annotate({ description: "Notebook version returned by notebook_read" })
const Index = NonNegativeInt.annotate({ description: "Zero-based cell index" })
const LIMIT = 20_000

function render(value: unknown) {
  const text = JSON.stringify(value, null, 2)
  if (text.length <= LIMIT) return text
  return `${text.slice(0, LIMIT)}
... notebook result truncated by CLI (${text.length - LIMIT} characters omitted)`
}

function abort(signal: AbortSignal) {
  return Effect.callback<never, HostError>((resume) => {
    const err = () => new HostError({ code: "cancelled", detail: "The notebook tool call was cancelled" })
    if (signal.aborted) return resume(Effect.fail(err()))
    const handler = () => resume(Effect.fail(err()))
    signal.addEventListener("abort", handler, { once: true })
    return Effect.sync(() => signal.removeEventListener("abort", handler))
  })
}

function run(effect: Effect.Effect<Result, HostError>, signal: AbortSignal) {
  return effect.pipe(Effect.raceFirst(abort(signal)), Effect.orDie)
}

const ReadParams = Schema.Struct({
  path: Path,
  include_outputs: Schema.optional(Schema.Boolean).annotate({
    description: "Include bounded text and error outputs. Defaults to false.",
  }),
})

export const NotebookReadTool = Tool.define<
  typeof ReadParams,
  { path: string; version: number },
  Notebook.Service,
  "notebook_read"
>(
  "notebook_read",
  Effect.gen(function* () {
    const notebook = yield* Notebook.Service
    return {
      description:
        "Read the live, possibly unsaved structure and source of one VS Code notebook. Outputs are omitted unless include_outputs is true.",
      parameters: ReadParams,
      execute: (params, ctx) =>
        Effect.gen(function* () {
          yield* ctx.ask({
            permission: "notebook_read",
            patterns: [params.path],
            always: [params.path],
            metadata: { path: params.path, includeOutputs: params.include_outputs === true },
          })
          const result = yield* run(
            notebook.request({
              operation: "read",
              sessionID: ctx.sessionID,
              path: params.path,
              includeOutputs: params.include_outputs === true,
            }),
            ctx.abort,
          )
          if (result.operation !== "read")
            return yield* Effect.die(new Error("Notebook host returned the wrong result type"))
          return {
            title: `Notebook: ${params.path}`,
            output: render(result),
            metadata: { path: result.path, version: result.version },
          }
        }),
    }
  }),
)

const Cell = {
  kind: Schema.Literals(["code", "markdown"]),
  language: Schema.optional(Schema.String.check(Schema.isMaxLength(200))),
  source: Source,
}
const EditParams = Schema.Union([
  Schema.Struct({ path: Path, expected_version: Version, index: Index, action: Schema.Literal("insert"), ...Cell }),
  Schema.Struct({ path: Path, expected_version: Version, index: Index, action: Schema.Literal("replace"), ...Cell }),
  Schema.Struct({ path: Path, expected_version: Version, index: Index, action: Schema.Literal("delete") }),
])

export const NotebookEditTool = Tool.define<
  typeof EditParams,
  { path: string; version: number; index: number },
  Notebook.Service,
  "notebook_edit"
>(
  "notebook_edit",
  Effect.gen(function* () {
    const notebook = yield* Notebook.Service
    return {
      description:
        "Insert, replace, or delete one cell in a live VS Code notebook. Requires the exact notebook version from notebook_read and leaves the document dirty.",
      parameters: EditParams,
      execute: (params, ctx) =>
        Effect.gen(function* () {
          yield* ctx.ask({
            permission: "notebook_edit",
            patterns: [params.path],
            always: [params.path],
            metadata: {
              path: params.path,
              action: params.action,
              index: params.index,
              version: params.expected_version,
            },
          })
          const edit =
            params.action === "delete"
              ? ({ action: params.action } as const)
              : { action: params.action, kind: params.kind, language: params.language, source: params.source }
          const result = yield* run(
            notebook.request({
              operation: "edit",
              sessionID: ctx.sessionID,
              path: params.path,
              version: params.expected_version,
              index: params.index,
              edit,
            }),
            ctx.abort,
          )
          if (result.operation !== "edit")
            return yield* Effect.die(new Error("Notebook host returned the wrong result type"))
          return {
            title: `${result.action} notebook cell ${result.index}`,
            output: render(result),
            metadata: { path: result.path, version: result.version, index: result.index },
          }
        }),
    }
  }),
)

const ExecuteParams = Schema.Struct({ path: Path, expected_version: Version, index: Index })

export const NotebookExecuteTool = Tool.define<
  typeof ExecuteParams,
  { path: string; version: number; index: number },
  Notebook.Service,
  "notebook_execute"
>(
  "notebook_execute",
  Effect.gen(function* () {
    const notebook = yield* Notebook.Service
    return {
      description:
        "Execute one explicit code cell in a live VS Code notebook without revealing the notebook or opening a kernel picker. Requires the exact notebook version from notebook_read.",
      parameters: ExecuteParams,
      execute: (params, ctx) =>
        Effect.gen(function* () {
          yield* ctx.ask({
            permission: "notebook_execute",
            patterns: [params.path],
            always: [params.path],
            metadata: { path: params.path, index: params.index, version: params.expected_version },
          })
          const result = yield* run(
            notebook.request({
              operation: "execute",
              sessionID: ctx.sessionID,
              path: params.path,
              version: params.expected_version,
              index: params.index,
            }),
            ctx.abort,
          )
          if (result.operation !== "execute")
            return yield* Effect.die(new Error("Notebook host returned the wrong result type"))
          return {
            title: `Executed notebook cell ${result.index}`,
            output: render(result),
            metadata: { path: result.path, version: result.version, index: result.index },
          }
        }),
    }
  }),
)
