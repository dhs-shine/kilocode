import { Effect } from "effect"
import { dirname } from "node:path"
import type { AppFileSystem } from "@opencode-ai/core/filesystem"
import * as Encoding from "../encoding"

/**
 * Encoding-aware file operations routed through the application's filesystem
 * capability so active sandbox profiles apply to tool writes.
 */

const wrap = (cause: unknown) => (cause instanceof Error ? cause : new Error(String(cause)))

export const read = (fs: AppFileSystem.Interface, path: string) =>
  Effect.gen(function* () {
    const bytes = yield* fs.readFile(path).pipe(Effect.mapError(wrap))
    const data = Buffer.from(bytes)
    const encoding = Encoding.detect(data)
    return { text: Encoding.decode(data, encoding), encoding }
  })

export const write = (fs: AppFileSystem.Interface, path: string, text: string, encoding: string = Encoding.DEFAULT) =>
  Effect.gen(function* () {
    yield* fs.ensureDir(dirname(path)).pipe(Effect.mapError(wrap))
    yield* fs.writeFile(path, Encoding.encode(text, encoding)).pipe(Effect.mapError(wrap))
  })
