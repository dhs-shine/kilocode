import { describe, expect, it } from "bun:test"
import type { QuestionRequest } from "../../webview-ui/src/types/messages"
import { removeQuestion, restoreQuestion } from "../../webview-ui/src/context/question-queue"

const first: QuestionRequest = {
  id: "req-1",
  sessionID: "ses-1",
  questions: [],
}

const second: QuestionRequest = {
  id: "req-2",
  sessionID: "ses-1",
  questions: [],
}

describe("question queue", () => {
  it("removes only the question being answered", () => {
    const result = removeQuestion([first, second], first.id)

    expect(result.question).toBe(first)
    expect(result.questions).toEqual([second])
  })

  it("restores a failed question without duplicating it", () => {
    expect(restoreQuestion([second], first)).toEqual([second, first])
    expect(restoreQuestion([first, second], first)).toEqual([first, second])
  })
})
