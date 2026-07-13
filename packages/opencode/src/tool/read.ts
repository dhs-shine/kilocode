import { Effect, Schema, Scope } from "effect" // kilocode_change - stable object reads do not use Option
import { NonNegativeInt } from "@opencode-ai/core/schema"
import * as path from "path"
import { Readable } from "stream" // kilocode_change
import { createInterface } from "readline"
import * as Tool from "./tool"
import { AppFileSystem } from "@opencode-ai/core/filesystem"
import { LSP } from "@/lsp/lsp"
import DESCRIPTION from "./read.txt"
import { InstanceState } from "@/effect/instance-state"
import { assertExternalDirectoryEffect } from "./external-directory"
import { Instruction } from "../session/instruction"
import { isPdfAttachment, sniffAttachmentMime } from "@/util/media"
import { Reference } from "@/reference/reference"
// kilocode_change start
import * as Encoding from "../kilocode/encoding"
import { KiloReference } from "@/kilocode/reference/contains"
import { KiloReadObject } from "@/kilocode/tool/read-object"
import * as Extract from "../kilocode/tool/read-extract"
import * as TextStream from "../kilocode/text-stream"
// kilocode_change end

const DEFAULT_READ_LIMIT = 2000
const MAX_LINE_LENGTH = 2000
// kilocode_change start - report the safe Unicode slice length
const suffix = (length: number) => `... (line truncated to ${length} chars)`
// kilocode_change end
const MAX_BYTES = 50 * 1024
const MAX_BYTES_LABEL = `${MAX_BYTES / 1024} KB`
const SAMPLE_BYTES = 4096
const SUPPORTED_IMAGE_MIMES = new Set(["image/jpeg", "image/png", "image/gif", "image/webp"])

// `offset` and `limit` were originally `z.coerce.number()` — the runtime
// coercion was useful when the tool was called from a shell but serves no
// purpose in the LLM tool-call path (the model emits typed JSON). The JSON
// Schema output is identical (`type: "number"`), so the LLM view is
// unchanged; purely CLI-facing uses must now send numbers rather than strings.
export const Parameters = Schema.Struct({
  filePath: Schema.String.annotate({ description: "The absolute path to the file or directory to read" }),
  offset: Schema.optional(NonNegativeInt).annotate({
    description: "The line number to start reading from (1-indexed)",
  }),
  limit: Schema.optional(NonNegativeInt).annotate({
    description: "The maximum number of lines to read (defaults to 2000)",
  }),
})

export const ReadTool = Tool.define(
  "read",
  Effect.gen(function* () {
    const fs = yield* AppFileSystem.Service
    const instruction = yield* Instruction.Service
    const lsp = yield* LSP.Service
    const reference = yield* Reference.Service
    const scope = yield* Scope.Scope

    // kilocode_change start - canonicalize missing-file parents before suggestion disclosure
    const miss = Effect.fn("ReadTool.miss")(function* (filepath: string, ctx: Tool.Context) {
      const dir = path.dirname(filepath)
      const base = path.basename(filepath)
      const parent = yield* KiloReadObject.directory(dir).pipe(Effect.option)
      if (parent._tag === "None") return yield* Effect.fail(new Error(`File not found: ${filepath}`))
      yield* assertExternalDirectoryEffect(ctx, parent.value.target, { bypass: false, kind: "directory" })
      const items = parent.value.items
        .filter(
          (item) => item.toLowerCase().includes(base.toLowerCase()) || base.toLowerCase().includes(item.toLowerCase()),
        )
        .map((item) => path.join(parent.value.target, item))
        .slice(0, 3)

      if (items.length > 0) {
        return yield* Effect.fail(
          new Error(`File not found: ${filepath}\n\nDid you mean one of these?\n${items.join("\n")}`),
        )
      }

      return yield* Effect.fail(new Error(`File not found: ${filepath}`))
    })
    // kilocode_change end

    const warm = Effect.fn("ReadTool.warm")(function* (filepath: string) {
      yield* lsp.touchFile(filepath).pipe(Effect.ignore, Effect.forkIn(scope))
    })

    // kilocode_change start - extracted formats and text consume the authorized open object
    const lines = Effect.fn("ReadTool.lines")(
      (file: KiloReadObject.File, opts: { limit: number; offset: number }, abort: AbortSignal) =>
        Effect.tryPromise({
          try: async (signal) => {
            const combined = AbortSignal.any([abort, signal])
            const extracted = Extract.accepts(file.requested)
              ? await Extract.open(file.requested, await file.read(Extract.limit(file.requested), combined))
              : undefined
            if (extracted) return collect(TextStream.abortable(extracted, combined), opts)
            return TextStream.withFallback(
              () => file.stream(combined),
              (next) => file.read(undefined, next),
              (stream) => collect(stream, opts),
              combined,
            )
          },
          catch: (err) => (err instanceof Error ? err : new Error(String(err))),
        }),
    )
    // kilocode_change end

    const isBinaryFile = (filepath: string, bytes: Uint8Array) => {
      const ext = path.extname(filepath).toLowerCase()
      switch (ext) {
        case ".zip":
        case ".tar":
        case ".gz":
        case ".exe":
        case ".dll":
        case ".so":
        case ".class":
        case ".jar":
        case ".war":
        case ".7z":
        case ".doc":
        case ".docx":
        case ".xls":
        case ".xlsx":
        case ".ppt":
        case ".pptx":
        case ".odt":
        case ".ods":
        case ".odp":
        case ".bin":
        case ".dat":
        case ".obj":
        case ".o":
        case ".a":
        case ".lib":
        case ".wasm":
        case ".pyc":
        case ".pyo":
          return true
      }

      if (bytes.length === 0) return false

      // kilocode_change start - UTF-16/32 BOM: NUL bytes are legitimate, skip the NUL/control-char heuristic
      const buf = Buffer.from(bytes.buffer, bytes.byteOffset, bytes.byteLength)
      if (Encoding.hasUtf16Bom(buf, bytes.length) || Encoding.hasUtf32Bom(buf, bytes.length)) return false
      // kilocode_change end

      let nonPrintableCount = 0
      for (let i = 0; i < bytes.length; i++) {
        if (bytes[i] === 0) return true
        if (bytes[i] < 9 || (bytes[i] > 13 && bytes[i] < 32)) {
          nonPrintableCount++
        }
      }

      return nonPrintableCount / bytes.length > 0.3
    }

    const run = Effect.fn("ReadTool.execute")(function* (
      params: Schema.Schema.Type<typeof Parameters>,
      ctx: Tool.Context,
    ) {
      const instance = yield* InstanceState.context
      let filepath = params.filePath
      if (!path.isAbsolute(filepath)) {
        filepath = path.resolve(instance.directory, filepath)
      }
      if (process.platform === "win32") {
        filepath = AppFileSystem.normalizePath(filepath)
      }
      const requested = filepath
      yield* reference.ensure(requested)
      const title = path.relative(instance.worktree, requested)
      // kilocode_change start - fail before read authorization when the target is missing
      const info = yield* fs.stat(requested).pipe(
        Effect.catchIf(
          (err) => "reason" in err && err.reason._tag === "NotFound",
          () => Effect.succeed(undefined),
        ),
      )
      if (!info) {
        return yield* miss(requested, ctx)
      }
      // kilocode_change end

      // kilocode_change start - directory mentions expose only a bound listing, never child file bodies
      if (info.type === "Directory") {
        const directory = yield* KiloReadObject.directory(requested)
        const target = directory.target
        const explicit =
          typeof ctx.extra?.["referenceRoot"] === "string" &&
          (yield* KiloReference.path(fs, ctx.extra["referenceRoot"], target))
        const referenced =
          explicit ||
          ((yield* reference.contains(requested)) &&
            (yield* KiloReference.contains({ fs, references: reference, target })))
        yield* assertExternalDirectoryEffect(ctx, target, { bypass: referenced, kind: "directory" })
        yield* ctx.ask({
          permission: "read",
          patterns: [...new Set([requested, target].map((item) => path.relative(instance.worktree, item)))],
          always: ["*"],
          metadata: {},
        })
        const items = directory.items
        const limit = Math.max(1, params.limit ?? DEFAULT_READ_LIMIT) // kilocode_change - prevent zero-limit loops
        const offset = params.offset || 1
        const start = offset - 1
        const sliced = items.slice(start, start + limit)
        const truncated = start + sliced.length < items.length

        return {
          title,
          output: [
            `<path>${target}</path>`,
            `<type>directory</type>`,
            `<entries>`,
            sliced.join("\n"),
            truncated
              ? `\n(Showing ${sliced.length} of ${items.length} entries. Use 'offset' parameter to read beyond entry ${offset + sliced.length})`
              : `\n(${items.length} entries)`,
            `</entries>`,
          ].join("\n"),
          metadata: {
            preview: sliced.slice(0, 20).join("\n"),
            truncated,
            loaded: [],
          },
        }
      }
      // kilocode_change end

      // kilocode_change start - hold one object open across authorization and every content read
      return yield* KiloReadObject.use(requested, (bound) =>
        Effect.gen(function* () {
          if (!bound.stat.isFile()) return yield* Effect.fail(new Error(`Cannot read non-file: ${requested}`))
          const explicit =
            typeof ctx.extra?.["referenceRoot"] === "string" &&
            (yield* KiloReference.path(fs, ctx.extra["referenceRoot"], bound.target))
          const referenced =
            explicit ||
            ((yield* reference.contains(requested)) &&
              (yield* KiloReference.contains({ fs, references: reference, target: bound.target })))
          yield* assertExternalDirectoryEffect(ctx, bound.target, { bypass: referenced, kind: "file" })
          yield* ctx.ask({
            permission: "read",
            patterns: [...new Set([requested, bound.target].map((item) => path.relative(instance.worktree, item)))],
            always: ["*"],
            metadata: {},
          })

          const loaded =
            ctx.extra?.["includeInstructions"] === false
              ? []
              : yield* instruction.resolve(ctx.messages, bound.target, ctx.messageID)
          const sample = yield* Effect.tryPromise({
            try: (signal) => bound.sample(SAMPLE_BYTES, AbortSignal.any([ctx.abort, signal])),
            catch: (err) => (err instanceof Error ? err : new Error(String(err))),
          })
          const mime = sniffAttachmentMime(sample, AppFileSystem.mimeType(requested))
          const isImage = SUPPORTED_IMAGE_MIMES.has(mime)

          if (isImage || isPdfAttachment(mime)) {
            const bytes = yield* Effect.tryPromise({
              try: (signal) => bound.read(undefined, AbortSignal.any([ctx.abort, signal])),
              catch: (err) => (err instanceof Error ? err : new Error(String(err))),
            })
            const msg = isPdfAttachment(mime) ? "PDF read successfully" : "Image read successfully"
            return {
              title,
              output: msg,
              metadata: { preview: msg, truncated: false, loaded: loaded.map((item) => item.filepath) },
              attachments: [{ type: "file" as const, mime, url: `data:${mime};base64,${bytes.toString("base64")}` }],
            }
          }

          if (!Extract.binary(requested) && isBinaryFile(requested, sample)) {
            return yield* Effect.fail(new Error(`Cannot read binary file: ${requested}`))
          }
          const file = yield* lines(
            bound,
            { limit: Math.max(1, params.limit ?? DEFAULT_READ_LIMIT), offset: params.offset || 1 },
            ctx.abort,
          )
          if (file.count < file.offset && !(file.count === 0 && file.offset === 1)) {
            return yield* Effect.fail(
              new Error(`Offset ${file.offset} is out of range for this file (${file.count} lines)`),
            )
          }

          let output = [`<path>${bound.target}</path>`, `<type>file</type>`, "<content>\n"].join("\n")
          output += file.raw.map((line, i) => `${i + file.offset}: ${line}`).join("\n")
          const last = file.offset + file.raw.length - 1
          const next = last + 1
          const truncated = file.more || file.cut
          if (file.cut) {
            output += `\n\n(Output capped at ${MAX_BYTES_LABEL}. Showing lines ${file.offset}-${last}. Use offset=${next} to continue.)`
          } else if (file.more) {
            output += `\n\n(Showing lines ${file.offset}-${last} of ${file.count}. Use offset=${next} to continue.)`
          } else {
            output += `\n\n(End of file - total ${file.count} lines)`
          }
          output += "\n</content>"
          yield* warm(bound.target)
          if (loaded.length > 0) {
            output += `\n\n<system-reminder>\n${loaded.map((item) => item.content).join("\n\n")}\n</system-reminder>`
          }
          return {
            title,
            output,
            metadata: {
              preview: file.raw.slice(0, 20).join("\n"),
              truncated,
              loaded: loaded.map((item) => item.filepath),
            },
          }
        }),
      )
      // kilocode_change end
    })

    return {
      description: DESCRIPTION,
      parameters: Parameters,
      execute: (params: Schema.Schema.Type<typeof Parameters>, ctx: Tool.Context) =>
        run(params, ctx).pipe(Effect.orDie),
    }
  }),
)

// kilocode_change start - extracted formats use native readers; ordinary text is supplied by AppFileSystem above
async function collect(stream: Readable, opts: { limit: number; offset: number }) {
  // kilocode_change end
  const rl = createInterface({ input: stream, crlfDelay: Infinity })
  const start = opts.offset - 1
  const raw: string[] = []
  let bytes = 0
  let count = 0
  let cut = false
  let more = false
  try {
    for await (const text of rl) {
      count += 1
      if (count <= start) continue
      if (raw.length >= opts.limit) {
        more = true
        continue
      }
      // kilocode_change start - keep truncated output valid Unicode
      const sliced = TextStream.safeSlice(text, MAX_LINE_LENGTH)
      const line = text.length > MAX_LINE_LENGTH ? sliced + suffix(sliced.length) : text
      // kilocode_change end
      const size = Buffer.byteLength(line, "utf-8") + (raw.length > 0 ? 1 : 0)
      if (bytes + size > MAX_BYTES) {
        cut = true
        more = true
        break
      }
      raw.push(line)
      bytes += size
    }
  } finally {
    rl.close()
    stream.destroy()
  }
  return { raw, count, cut, more, offset: opts.offset }
}
