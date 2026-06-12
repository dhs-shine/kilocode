import { describe, expect, test } from "bun:test"
import { KiloConnectionService } from "../../src/services/cli-backend/connection-service"

describe("KiloConnectionService question routing", () => {
  test("ignores stale NotFoundError rejects while draining questions", async () => {
    const service = new KiloConnectionService({} as any)
    const client = {
      permission: {
        list: async () => ({ data: [] }),
      },
      question: {
        list: async () => ({ data: [{ id: "que_test" }] }),
        reject: async () => ({ error: { _tag: "NotFound" } }),
      },
      suggestion: {
        list: async () => ({ data: [] }),
      },
      network: {
        list: async () => ({ data: [] }),
      },
    }

    ;(service as any).client = client
    ;(service as any).directoryProviders.add(() => ["/tmp/workspace"])

    await expect(service.drainPendingPrompts()).resolves.toBeUndefined()
  })

  test("records and clears request origins from SSE events", () => {
    const service = new KiloConnectionService({} as any)
    const handler = service as unknown as {
      handleQuestionEvent(event: unknown, directory?: string): void
    }

    handler.handleQuestionEvent(
      { type: "question.asked", properties: { id: "que_test", sessionID: "ses_test", questions: [] } },
      "/tmp/worktree",
    )
    expect(service.getQuestionDirectory("que_test")).toBe("/tmp/worktree")

    handler.handleQuestionEvent({
      type: "question.replied",
      properties: { requestID: "que_test", sessionID: "ses_test", answers: [] },
    })
    expect(service.getQuestionDirectory("que_test")).toBeUndefined()

    service.recordQuestionDirectory("que_rejected", "/tmp/worktree")
    handler.handleQuestionEvent({
      type: "question.rejected",
      properties: { requestID: "que_rejected", sessionID: "ses_test" },
    })
    expect(service.getQuestionDirectory("que_rejected")).toBeUndefined()
  })
})
