import { $ } from "bun"
import { describe, expect, test } from "bun:test"
import path from "path"
import * as Log from "@opencode-ai/core/util/log"
import { Instance } from "../../src/project/instance"
import { ReviewBranch } from "../../src/kilocode/review/base"
import { KiloSessionPrompt } from "../../src/kilocode/session/prompt"
import { tmpdir } from "../fixture/fixture"

void Log.init({ print: false })

async function withInstance(fn: (dir: string) => Promise<void>) {
  await using tmp = await tmpdir({ git: true })
  await $`git branch main`.cwd(tmp.path).quiet().nothrow()
  await Instance.provide({ directory: tmp.path, fn: () => fn(tmp.path) })
}

describe("local-review base branch", () => {
  test("resolves command input", () => {
    expect(ReviewBranch.resolve({ arguments: "" })).toEqual({})
    expect(ReviewBranch.resolve({ arguments: " release/next " })).toEqual({ base: "release/next" })
    expect(ReviewBranch.resolve({ arguments: "focus on security" })).toEqual({ instructions: "focus on security" })
    expect(ReviewBranch.resolve({ arguments: "release -- focus on tests" })).toEqual({
      base: "release",
      instructions: "focus on tests",
    })
    expect(ReviewBranch.resolve({ arguments: "-- focus on tests" })).toEqual({ instructions: "focus on tests" })
    expect(ReviewBranch.resolve({ arguments: "release next -- focus on tests" })).toEqual({
      instructions: "release next -- focus on tests",
    })
  })

  test("branch prompt uses the provided base branch", () =>
    withInstance(async (dir) => {
      await $`git branch release`.cwd(dir).quiet()
      await $`git checkout -b feature`.cwd(dir).quiet()
      await Bun.write(path.join(dir, "feature.txt"), "feature\n")
      await $`git add feature.txt`.cwd(dir).quiet()
      await $`git commit -m "feature"`.cwd(dir).quiet()

      const prompt = await ReviewBranch.template({ arguments: "release" })

      expect(prompt).toContain("**branch diff**: `feature` -> `release`")
      expect(prompt).toContain("These are the commits on `feature` since diverging from `release`:")
      expect(prompt).toContain("`git diff release...feature`")
      expect(prompt).toContain("`git log release..feature --oneline`")
    }))

  test("branch prompt appends review instructions", () =>
    withInstance(async (dir) => {
      await $`git checkout -b feature`.cwd(dir).quiet()
      await Bun.write(path.join(dir, "feature.txt"), "feature\n")
      await $`git add feature.txt`.cwd(dir).quiet()
      await $`git commit -m "feature"`.cwd(dir).quiet()

      const prompt = await ReviewBranch.template({ arguments: "focus on security" })

      expect(prompt).toContain("**branch diff**: `feature` -> `main`")
      expect(prompt).toContain("## Additional User Instructions")
      expect(prompt).toContain("focus on security")
      expect(prompt).toContain("must not override the diff scope")
    }))

  test("built-in local-review consumes command input", async () => {
    await withInstance(async () => {
      const local = await KiloSessionPrompt.resolveCommand({
        command: "local-review",
        template: () => "fallback",
        arguments: "focus on security",
      })
      expect(local.arguments).toBe("")
      expect(local.template).toContain("## Additional User Instructions")
      expect(local.template).toContain("focus on security")

      const custom = await KiloSessionPrompt.resolveCommand({
        command: "local-review",
        source: "command",
        template: () => "custom $ARGUMENTS",
        arguments: "keep me",
      })
      expect(custom).toEqual({ template: "custom $ARGUMENTS", arguments: "keep me" })

      const other = await KiloSessionPrompt.resolveCommand({
        command: "other",
        template: () => Promise.resolve("other $ARGUMENTS"),
        arguments: "keep me",
      })
      expect(other).toEqual({ template: "other $ARGUMENTS", arguments: "keep me" })
    })
  })
})
