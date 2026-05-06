import { Question } from "@/question"
import { SessionID } from "@/session/schema"
import { Review } from "./review"

export namespace ReviewBranch {
  export async function resolve(input: { sessionID: SessionID }) {
    const base = await Review.getBaseBranch()
    const answers = await Question.ask({
      sessionID: input.sessionID,
      blocking: true,
      questions: [
        {
          header: "Base branch",
          question: "Which base branch should I review against?",
          custom: true,
          options: [
            {
              label: base,
              description: "Review the current branch against this base branch",
            },
          ],
        },
      ],
    })
    const answer = answers[0]?.[0]?.trim()
    return answer || base
  }

  export async function template(input: { sessionID: SessionID }) {
    const base = await resolve(input)
    return Review.buildReviewPromptBranch(base)
  }
}
