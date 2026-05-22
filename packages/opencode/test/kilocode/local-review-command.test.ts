import { describe, expect, test } from "bun:test"
import { localReviewCommand, localReviewUncommittedCommand } from "../../src/kilocode/review/command"

describe("local-review command", () => {
  const cmd = localReviewCommand()

  test("exposes a static string template", () => {
    expect(cmd.name).toBe("local-review")
    expect(typeof cmd.template).toBe("string")
  })

  test("template includes $ARGUMENTS for raw user input", () => {
    expect(cmd.template).toContain("$ARGUMENTS")
  })

  test("hints expose $ARGUMENTS as the only placeholder", () => {
    expect(cmd.hints).toEqual(["$ARGUMENTS"])
  })

  test("template documents the preserved argument syntax", () => {
    const text = cmd.template as string
    expect(text).toContain("Empty input")
    expect(text).toContain("single non-whitespace token")
    expect(text).toContain("<base> -- <instructions>")
    expect(text).toContain("-- <instructions>")
    expect(text).toContain("Multi-word input")
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

  test("template instructs the model to validate the base before reviewing", () => {
    const text = cmd.template as string
    expect(text).toContain("git merge-base HEAD <base>")
    expect(text).toMatch(/no common history|not found/i)
  })

  test("template tells the model not to edit files", () => {
    const text = cmd.template as string
    expect(text).toContain("DO NOT modify any files")
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

describe("local-review-uncommitted command", () => {
  const cmd = localReviewUncommittedCommand()

  test("exposes a static string template", () => {
    expect(cmd.name).toBe("local-review-uncommitted")
    expect(typeof cmd.template).toBe("string")
  })

  test("template includes $ARGUMENTS for raw user input", () => {
    expect(cmd.template).toContain("$ARGUMENTS")
  })

  test("hints expose $ARGUMENTS as the only placeholder", () => {
    expect(cmd.hints).toEqual(["$ARGUMENTS"])
  })

  test("template appends $ARGUMENTS at the end as raw input", () => {
    const text = cmd.template as string
    expect(text.trim().endsWith("$ARGUMENTS")).toBe(true)
  })

  test("template does not wrap user input in additional-instructions framing", () => {
    const text = cmd.template as string
    expect(text).not.toContain("Additional User Instructions")
  })

  test("template documents the uncommitted scope and key git commands", () => {
    const text = cmd.template as string
    expect(text).toMatch(/git\b[^\n]*\bdiff HEAD/)
    expect(text).toMatch(/git\b[^\n]*\bdiff --cached/)
    expect(text).toContain("git ls-files --others --exclude-standard")
  })

  test("template tells the model not to edit files", () => {
    const text = cmd.template as string
    expect(text).toContain("DO NOT modify any files")
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
