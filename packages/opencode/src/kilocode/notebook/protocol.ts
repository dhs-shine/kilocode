import { BusEvent } from "@/bus/bus-event"
import { SessionID } from "@/session/schema"
import { NonNegativeInt } from "@opencode-ai/core/schema"
import { Schema } from "effect"

export const RequestID = Schema.String.pipe(Schema.brand("NotebookRequestID")).annotate({
  identifier: "NotebookRequestID",
})
export type RequestID = Schema.Schema.Type<typeof RequestID>

export const Path = Schema.String.check(
  Schema.isMinLength(1),
  Schema.isMaxLength(4096),
  Schema.makeFilter((value: string) => {
    const parts = value.replaceAll("\\", "/").split("/")
    return value.startsWith("/") || /^[A-Za-z]:[\\/]/.test(value) || parts.includes("..")
      ? "Notebook path must be workspace-relative and contained in the workspace"
      : undefined
  }),
).annotate({ description: "Workspace-relative notebook path" })
const Source = Schema.String.check(Schema.isMaxLength(200_000))
const Text = Schema.String.check(Schema.isMaxLength(100_000))
const Version = NonNegativeInt.annotate({ description: "Expected VS Code notebook document version" })
const Index = NonNegativeInt.annotate({ description: "Zero-based cell index" })

export const Output = Schema.Struct({
  mime: Schema.String.check(Schema.isMaxLength(200)),
  text: Schema.optional(Text),
  name: Schema.optional(Schema.String.check(Schema.isMaxLength(500))),
  message: Schema.optional(Schema.String.check(Schema.isMaxLength(10_000))),
  stack: Schema.optional(Schema.String.check(Schema.isMaxLength(50_000))),
  omitted: Schema.optional(Schema.Boolean),
  truncated: Schema.optional(Schema.Boolean),
}).annotate({ identifier: "NotebookOutput" })
export type Output = Schema.Schema.Type<typeof Output>

export const Cell = Schema.Struct({
  index: Index,
  kind: Schema.Literals(["code", "markdown"]),
  language: Schema.String.check(Schema.isMaxLength(200)),
  source: Source,
  execution: Schema.optional(
    Schema.Struct({
      order: Schema.optional(NonNegativeInt),
      success: Schema.optional(Schema.Boolean),
      started: Schema.optional(NonNegativeInt),
      ended: Schema.optional(NonNegativeInt),
    }),
  ),
  outputs: Schema.optional(Schema.Array(Output).check(Schema.isMaxLength(100))),
}).annotate({ identifier: "NotebookCell" })
export type Cell = Schema.Schema.Type<typeof Cell>

const Base = { id: RequestID, sessionID: SessionID, path: Path }

export const ReadRequest = Schema.Struct({
  ...Base,
  operation: Schema.Literal("read"),
  includeOutputs: Schema.Boolean,
}).annotate({ identifier: "NotebookReadRequest" })

const CellEdit = {
  kind: Schema.Literals(["code", "markdown"]),
  language: Schema.optional(Schema.String.check(Schema.isMaxLength(200))),
  source: Source,
}

export const EditRequest = Schema.Struct({
  ...Base,
  operation: Schema.Literal("edit"),
  version: Version,
  index: Index,
  edit: Schema.Union([
    Schema.Struct({ action: Schema.Literal("insert"), ...CellEdit }),
    Schema.Struct({ action: Schema.Literal("replace"), ...CellEdit }),
    Schema.Struct({ action: Schema.Literal("delete") }),
  ]),
}).annotate({ identifier: "NotebookEditRequest" })

export const ExecuteRequest = Schema.Struct({
  ...Base,
  operation: Schema.Literal("execute"),
  version: Version,
  index: Index,
}).annotate({ identifier: "NotebookExecuteRequest" })

export const Request = Schema.Union([ReadRequest, EditRequest, ExecuteRequest]).annotate({
  identifier: "NotebookRequest",
})
export type Request = Schema.Schema.Type<typeof Request>

export const ReadResult = Schema.Struct({
  operation: Schema.Literal("read"),
  path: Path,
  version: Version,
  cells: Schema.Array(Cell).check(Schema.isMaxLength(2_000)),
  truncated: Schema.optional(Schema.Boolean),
})
  .check(
    Schema.makeFilter((value) =>
      JSON.stringify(value).length <= 2_000_000 ? undefined : "Notebook read result exceeds the aggregate output limit",
    ),
  )
  .annotate({ identifier: "NotebookReadResult" })

export const EditResult = Schema.Struct({
  operation: Schema.Literal("edit"),
  path: Path,
  version: Version,
  index: Index,
  action: Schema.Literals(["insert", "replace", "delete"]),
}).annotate({ identifier: "NotebookEditResult" })

export const ExecuteResult = Schema.Struct({
  operation: Schema.Literal("execute"),
  path: Path,
  version: Version,
  index: Index,
  status: Schema.Literals(["success", "error", "cancelled"]),
  outputs: Schema.Array(Output).check(Schema.isMaxLength(100)),
  truncated: Schema.optional(Schema.Boolean),
})
  .check(
    Schema.makeFilter((value) =>
      JSON.stringify(value).length <= 2_000_000
        ? undefined
        : "Notebook execution result exceeds the aggregate output limit",
    ),
  )
  .annotate({ identifier: "NotebookExecuteResult" })

export const Result = Schema.Union([ReadResult, EditResult, ExecuteResult]).annotate({ identifier: "NotebookResult" })
export type Result = Schema.Schema.Type<typeof Result>

export const ErrorCode = Schema.Literals([
  "cancelled",
  "closed",
  "disconnected",
  "execution_failed",
  "invalid_cell",
  "invalid_path",
  "no_kernel",
  "not_found",
  "stale_version",
  "timeout",
  "unsupported",
])
export type ErrorCode = Schema.Schema.Type<typeof ErrorCode>

export const Failure = Schema.Struct({
  code: ErrorCode,
  message: Schema.String.check(Schema.isMinLength(1), Schema.isMaxLength(10_000)),
}).annotate({ identifier: "NotebookFailure" })
export type Failure = Schema.Schema.Type<typeof Failure>

export const Event = {
  Requested: BusEvent.define("kilocode.notebook.requested", Request),
  Cancelled: BusEvent.define(
    "kilocode.notebook.cancelled",
    Schema.Struct({
      requestID: RequestID,
      sessionID: SessionID,
      reason: Schema.Literals(["cancelled", "disposed", "timeout"]),
    }),
  ),
}
