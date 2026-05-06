import { $ } from "bun"
import { describe, expect, test } from "bun:test"
import path from "path"
import { Effect } from "effect"
import * as Log from "@opencode-ai/core/util/log"
import { Instance } from "../../src/project/instance"
import { Question } from "../../src/question"
import { ReviewBranch } from "../../src/kilocode/review/base"
import { Review } from "../../src/kilocode/review/review"
import { SessionID } from "../../src/session/schema"
import { SessionPrompt } from "../../src/session/prompt"
import { tmpdir } from "../fixture/fixture"

void Log.init({ print: false })

async function withInstance(fn: (dir: string) => Promise<void>) {
  await using tmp = await tmpdir({ git: true })
  await $`git branch main`.cwd(tmp.path).quiet().nothrow()
  await Instance.provide({ directory: tmp.path, fn: () => fn(tmp.path) })
}

async function wait(sessionID: SessionID) {
  for (const _ of Array.from({ length: 50 })) {
    const list = await Question.list()
    const question = list.find((item) => item.sessionID === sessionID)
    if (question) return question
    await Bun.sleep(10)
  }
  throw new Error("timed out waiting for question")
}

function run<A, E>(fx: Effect.Effect<A, E, SessionPrompt.Service>) {
  return Effect.runPromise(fx.pipe(Effect.scoped, Effect.provide(SessionPrompt.defaultLayer)))
}

describe("local-review base branch", () => {
  test("built-in local-review asks for a base branch before continuing", () =>
    withInstance(async () => {
      const sessionID = SessionID.make("ses_local_review_base")
      const pending = run(
        Effect.gen(function* () {
          const prompt = yield* SessionPrompt.Service
          return yield* prompt.command({
            sessionID,
            command: "local-review",
            arguments: "",
          })
        }),
      ).catch((err) => err)

      const question = await wait(sessionID)
      expect(question.blocking).toBe(true)
      expect(question.questions).toHaveLength(1)
      expect(question.questions[0]?.header).toBe("Base branch")
      expect(question.questions[0]?.custom).toBe(true)
      expect(question.questions[0]?.options[0]?.label).toBe("main")

      await Question.reject(question.id)
      expect(await pending).toBeInstanceOf(Question.RejectedError)
      expect(await Question.list()).toEqual([])
    }))

  test("base resolver returns a typed custom branch", () =>
    withInstance(async () => {
      const sessionID = SessionID.make("ses_local_review_custom")
      const pending = ReviewBranch.resolve({ sessionID })
      const question = await wait(sessionID)

      await Question.reply({
        requestID: question.id,
        answers: [[" release/next "]],
      })

      await expect(pending).resolves.toBe("release/next")
      expect(await Question.list()).toEqual([])
    }))

  test("branch prompt uses the provided base branch", () =>
    withInstance(async (dir) => {
      await $`git branch release`.cwd(dir).quiet()
      await $`git checkout -b feature`.cwd(dir).quiet()
      await Bun.write(path.join(dir, "feature.txt"), "feature\n")
      await $`git add feature.txt`.cwd(dir).quiet()
      await $`git commit -m "feature"`.cwd(dir).quiet()

      const prompt = await Review.buildReviewPromptBranch("release")

      expect(prompt).toContain("**branch diff**: `feature` -> `release`")
      expect(prompt).toContain("These are the commits on `feature` since diverging from `release`:")
      expect(prompt).toContain("`git diff release...feature`")
      expect(prompt).toContain("`git log release..feature --oneline`")
    }))
})
