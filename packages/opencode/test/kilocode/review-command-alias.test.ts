import { describe, expect } from "bun:test"
import { CrossSpawnSpawner } from "@opencode-ai/core/cross-spawn-spawner"
import { Effect, Layer } from "effect"
import { Command } from "../../src/command"
import { resolvePrompt } from "../../src/kilocode/suggestion/tool"
import { provideTmpdirInstance } from "../fixture/fixture"
import { testEffect } from "../lib/effect"

const it = testEffect(Layer.mergeAll(Command.defaultLayer, CrossSpawnSpawner.defaultLayer))

describe("review command aliases", () => {
  it.live("resolves legacy review names without listing them", () =>
    provideTmpdirInstance(
      () =>
        Effect.gen(function* () {
          const command = yield* Command.Service
          const branch = yield* command.get("local-review")
          const uncommitted = yield* command.get("local-review-uncommitted")
          const prompt = yield* resolvePrompt("/local-review-uncommitted --focus tests", command)
          const list = yield* command.list()
          const names = list.map((item) => item.name)

          expect(branch?.template).toContain("local branch review")
          expect(uncommitted?.template).toContain("local uncommitted review")
          expect(prompt).toContain("## User Input\n\n--focus tests")
          expect(prompt).toContain("local uncommitted review")
          expect(prompt).not.toContain("$ARGUMENTS")
          expect(names).toContain("review")
          expect(names).not.toContain("local-review")
          expect(names).not.toContain("local-review-uncommitted")
        }),
      { git: true },
    ),
  )
})
