import type { Command } from "@/command"
import type { ReviewCommand } from "@kilocode/kilo-telemetry"
import REVIEW from "./review.txt"

function legacy(command: string | undefined) {
  if (command === "local-review") return "branch"
  if (command === "local-review-uncommitted") return "uncommitted"
}

export function isReviewCommand(command: string | undefined): command is ReviewCommand {
  return command === "review"
}

export function reviewCommandName(command: string | undefined): ReviewCommand | undefined {
  if (isReviewCommand(command)) return command
  if (legacy(command)) return "review"
}

export function parseReviewCommand(prompt: string | undefined): ReviewCommand | undefined {
  if (!prompt?.startsWith("/")) return
  const name = prompt.slice(1).split(/\s/, 1)[0]
  return reviewCommandName(name)
}

export function reviewCommand(): Command.Info {
  return {
    name: "review",
    description: "review changes [uncommitted|commit|branch|pr]",
    template: REVIEW,
    hints: ["$ARGUMENTS"],
  }
}

export function legacyReviewCommand(name: string): Command.Info | undefined {
  const scope = legacy(name)
  if (!scope) return
  return {
    name,
    description: "legacy review alias",
    template: REVIEW.replace("$ARGUMENTS", `${scope} $ARGUMENTS`),
    hints: ["$ARGUMENTS"],
  }
}
