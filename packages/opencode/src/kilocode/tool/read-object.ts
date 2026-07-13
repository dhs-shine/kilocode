import { open, readdir, realpath, stat, type FileHandle } from "node:fs/promises"
import { type BigIntStats } from "node:fs"
import { Readable } from "node:stream"
import { Effect } from "effect"
import { FSUtil } from "@opencode-ai/core/fs-util"

export namespace KiloReadObject {
  export class ChangedError extends Error {}

  export type File = {
    requested: string
    target: string
    handle: FileHandle
    stat: BigIntStats
    read: (limit?: number, signal?: AbortSignal) => Promise<Buffer>
    sample: (limit: number, signal?: AbortSignal) => Promise<Buffer>
    stream: (signal?: AbortSignal) => Readable
  }

  export type Directory = {
    target: string
    items: string[]
  }

  const failure = (err: unknown) => (err instanceof Error ? err : new Error(String(err)))

  async function bytes(handle: FileHandle, limit?: number, signal?: AbortSignal) {
    const chunks: Buffer[] = []
    const size = 64 * 1024
    const cap = limit === undefined ? Number.MAX_SAFE_INTEGER : limit
    let offset = 0
    while (offset < cap) {
      signal?.throwIfAborted()
      const buffer = Buffer.allocUnsafe(Math.min(size, cap - offset))
      const result = await handle.read(buffer, 0, buffer.length, offset)
      if (result.bytesRead === 0) break
      chunks.push(buffer.subarray(0, result.bytesRead))
      offset += result.bytesRead
    }
    return Buffer.concat(chunks, offset)
  }

  async function* chunks(handle: FileHandle, signal?: AbortSignal) {
    const size = 64 * 1024
    let offset = 0
    while (true) {
      signal?.throwIfAborted()
      const buffer = Buffer.allocUnsafe(size)
      const result = await handle.read(buffer, 0, buffer.length, offset)
      if (result.bytesRead === 0) return
      offset += result.bytesRead
      yield buffer.subarray(0, result.bytesRead)
    }
  }

  export function use<A, E, R>(requested: string, fn: (file: File) => Effect.Effect<A, E, R>) {
    const acquire = Effect.tryPromise({
      try: () => open(requested, "r"),
      catch: failure,
    })
    return Effect.acquireUseRelease(
      acquire,
      (handle) =>
        Effect.gen(function* () {
          const opened = yield* Effect.tryPromise({
            try: () => handle.stat({ bigint: true }),
            catch: failure,
          })
          const probe = process.platform === "linux" ? `/proc/self/fd/${handle.fd}` : requested
          const resolved = yield* Effect.tryPromise({
            try: () => realpath(probe),
            catch: failure,
          })
          const seen = yield* Effect.tryPromise({
            try: () => stat(resolved, { bigint: true }),
            catch: failure,
          })
          if (opened.dev !== seen.dev || opened.ino !== seen.ino) {
            return yield* Effect.fail(new ChangedError(`File changed while opening: ${requested}`))
          }
          const target = process.platform === "win32" ? FSUtil.normalizePath(resolved) : resolved
          return yield* fn({
            requested,
            target,
            handle,
            stat: opened,
            read: (limit, signal) => bytes(handle, limit, signal),
            sample: (limit, signal) => bytes(handle, limit, signal),
            stream: (signal) => Readable.from(chunks(handle, signal)),
          })
        }),
      (handle) => Effect.promise(() => handle.close()).pipe(Effect.catch(() => Effect.void)),
    )
  }

  export const directory = Effect.fn("KiloReadObject.directory")(function* (requested: string) {
    return yield* Effect.tryPromise({
      try: async () => {
        const opened = await stat(requested, { bigint: true })
        if (!opened.isDirectory()) throw new ChangedError(`Not a directory: ${requested}`)
        const resolved = await realpath(requested)
        const target = process.platform === "win32" ? FSUtil.normalizePath(resolved) : resolved
        const seen = await stat(resolved, { bigint: true })
        if (opened.dev !== seen.dev || opened.ino !== seen.ino) {
          throw new ChangedError(`Directory changed while opening: ${requested}`)
        }
        const entries = await readdir(resolved, { withFileTypes: true })
        const after = await stat(resolved, { bigint: true })
        const current = await realpath(requested)
        const canonical = process.platform === "win32" ? FSUtil.normalizePath(current) : current
        if (opened.dev !== after.dev || opened.ino !== after.ino || canonical !== target) {
          throw new ChangedError(`Directory changed while reading: ${requested}`)
        }
        return {
          target,
          items: entries
            .map((entry) => (entry.isDirectory() ? `${entry.name}/` : entry.name))
            .sort((a, b) => a.localeCompare(b)),
        } satisfies Directory
      },
      catch: failure,
    })
  })
}
