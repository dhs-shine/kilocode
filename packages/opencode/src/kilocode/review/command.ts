import type { Command } from "@/command"
import LOCAL_REVIEW from "./local-review.txt"
import LOCAL_REVIEW_UNCOMMITTED from "./local-review-uncommitted.txt"

/**
 * /local-review-uncommitted - local review (uncommitted changes)
 */
export function localReviewUncommittedCommand(): Command.Info {
  return {
    name: "local-review-uncommitted",
    description: "local review (uncommitted changes)",
    template: LOCAL_REVIEW_UNCOMMITTED,
    hints: ["$ARGUMENTS"],
  }
}

/**
 * /local-review - local review (current branch vs base)
 */
export function localReviewCommand(): Command.Info {
  return {
    name: "local-review",
    description: "local review (current branch, optional base or instructions)",
    template: LOCAL_REVIEW,
    hints: ["$ARGUMENTS"],
  }
}
