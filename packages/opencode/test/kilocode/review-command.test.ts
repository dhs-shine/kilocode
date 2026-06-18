import { describe, expect, test } from "bun:test"
import { parseReviewCommand, reviewCommand } from "../../src/kilocode/review/command"

function expectReviewFixContract(text: string) {
  expect(text).toContain("During the initial review phase")
  expect(text).toContain("DO NOT modify any files")
  expect(text).toContain("After the user chooses a fix option")
  expect(text).toContain("you may switch from review to implementation behavior")
  expect(text).toContain("Use editing tools to modify code only for findings in the completed review")
}

describe("review command parsing", () => {
  test("parses the review slash command", () => {
    expect(parseReviewCommand("/review")).toBe("review")
    expect(parseReviewCommand("/review uncommitted -- focus tests")).toBe("review")
    expect(parseReviewCommand("/local-review -- focus tests")).toBeUndefined()
    expect(parseReviewCommand("/local-review-uncommitted focus tests")).toBeUndefined()
    expect(parseReviewCommand("/test")).toBeUndefined()
    expect(parseReviewCommand("review")).toBeUndefined()
  })
})

describe("review command", () => {
  const cmd = reviewCommand()

  test("exposes a static string template", () => {
    expect(cmd.name).toBe("review")
    expect(typeof cmd.template).toBe("string")
  })

  test("template includes $ARGUMENTS for raw user input", () => {
    expect(cmd.template).toContain("$ARGUMENTS")
  })

  test("hints expose $ARGUMENTS as the only placeholder", () => {
    expect(cmd.hints).toEqual(["$ARGUMENTS"])
  })

  test("template documents scope and argument handling", () => {
    const text = cmd.template as string
    expect(text).toContain("Explicit uncommitted scope")
    expect(text).toContain("literal free-form text")
    expect(text).toContain("Clearly requested base")
    expect(text).toContain("Base plus guidance")
    expect(text).toContain("Explicit branch scope")
    expect(text).toContain("Empty or guidance-only input")
    expect(text).toContain("ambiguous input as review instructions")
    expect(text).not.toContain("<base> -- <instructions>")
    expect(text).not.toContain("-- <instructions>")
  })

  test("template documents the default base priority", () => {
    const text = cmd.template as string
    expect(text).toContain("origin/main")
    expect(text).toContain("origin/master")
    expect(text).toContain("origin/dev")
    expect(text).toContain("origin/develop")
    expect(text).toContain("local `main`")
    expect(text).toContain("local `master`")
    expect(text).toContain("local `dev`")
    expect(text).toContain("local `develop`")
    expect(text).toContain("fall back to `main`")
    expect(text).toContain("Review.getBaseBranch()")
  })

  test("template instructs the model to validate the base before branch review", () => {
    const text = cmd.template as string
    expect(text).toContain("git merge-base HEAD <base>")
    expect(text).toMatch(/no common history|not found/i)
  })

  test("template documents the uncommitted scope and key git commands", () => {
    const text = cmd.template as string
    expect(text).toContain("For uncommitted review")
    expect(text).toMatch(/git\b[^\n]*\bdiff HEAD/)
    expect(text).toMatch(/git\b[^\n]*\bdiff --cached/)
    expect(text).toContain("git ls-files --others --exclude-standard")
  })

  test("template avoids dereferencing untracked symlinks", () => {
    const text = cmd.template as string
    expect(text).toContain("verify it is not a symlink")
    expect(text).toContain("do not follow the link")
  })

  test("template scopes no-edit behavior to review phase", () => {
    const text = cmd.template as string
    expectReviewFixContract(text)
  })

  test("template applies the review-pr high-signal review focus", () => {
    const text = cmd.template as string
    expect(text).toContain("Review only these things")
    expect(text).toContain("deploy safety")
    expect(text).toContain("duplicated code or duplicated logic")
    expect(text).toContain("dead code caused by the reviewed changes")
    expect(text).toContain("Do not review these things")
    expect(text).toContain("code style")
    expect(text).toContain("generic refactors with no bug or product risk")
  })

  test("template applies the review-pr parallel review tracks", () => {
    const text = cmd.template as string
    expect(text).toContain("spawn six sub-agents in parallel")
    expect(text).toContain("security")
    expect(text).toContain("performance")
    expect(text).toContain("business logic")
    expect(text).toContain("NO_FINDINGS")
  })
})
