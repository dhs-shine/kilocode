import { Effect } from "effect"
import { AppFileSystem } from "@opencode-ai/core/filesystem"
import type { Reference } from "@/reference/reference"

export namespace KiloReference {
  export const contains = Effect.fn("KiloReference.contains")(function* (input: {
    fs: Pick<AppFileSystem.Interface, "realPath">
    references: Pick<Reference.Interface, "list">
    target: string
  }) {
    const target = yield* input.fs.realPath(input.target).pipe(Effect.catch(() => Effect.succeed(input.target)))
    const refs = yield* input.references.list()
    for (const reference of refs) {
      if (reference.kind !== "git") continue
      const root = yield* input.fs.realPath(reference.path).pipe(Effect.catch(() => Effect.succeed(reference.path)))
      if (AppFileSystem.contains(root, target)) return true
    }
    return false
  })
}
