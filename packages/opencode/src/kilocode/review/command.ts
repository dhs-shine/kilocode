import type { Command } from "@/command"
import type { ReviewCommand } from "@kilocode/kilo-telemetry"
import LOCAL from "./local-review.txt"
import UNCOMMITTED from "./local-review-uncommitted.txt"
import REVIEW from "./review.txt"

export function isReviewCommand(command: string | undefined): command is ReviewCommand {
  return command === "review"
}

export function parseReviewCommand(prompt: string | undefined): ReviewCommand | undefined {
  if (!prompt?.startsWith("/")) return
  const name = prompt.slice(1).split(/\s/, 1)[0]
  if (isReviewCommand(name)) return name
}

export function reviewCommand(): Command.Info {
  return {
    name: "review",
    description: "local code review",
    template: REVIEW,
    hints: ["$ARGUMENTS"],
  }
}

export function legacyReviewCommand(name: string): Command.Info | undefined {
  const uncommitted = name === "local-review-uncommitted"
  if (name !== "local-review" && !uncommitted) return
  return {
    name,
    description: uncommitted ? "local review (uncommitted changes)" : "local review (current branch, optional base or instructions)",
    template: uncommitted ? UNCOMMITTED : LOCAL,
    hints: ["$ARGUMENTS"],
  }
}
