import { Review } from "./review"

const argsRegex = /(?:\[Image\s+\d+\]|"[^"]*"|'[^']*'|[^\s"']+)/gi
const quoteTrimRegex = /^["']|["']$/g

export namespace ReviewBranch {
  export type Resolved = {
    base?: string
    instructions?: string
  }

  function tokens(input: string) {
    return (input.match(argsRegex) ?? []).map((arg) => arg.replace(quoteTrimRegex, ""))
  }

  function split(input: string) {
    const match = input.match(/(^|\s)--(?=\s|$)/)
    if (!match || match.index === undefined) return
    const start = match.index + (match[1]?.length ?? 0)
    return {
      before: input.slice(0, start).trim(),
      after: input.slice(start + 2).trim(),
    }
  }

  export function resolve(input: { arguments: string }): Resolved {
    const text = input.arguments.trim()
    if (!text) return {}

    const parts = split(text)
    if (parts) {
      const base = tokens(parts.before)
      if (base.length === 0 || (base.length === 1 && !/\s/.test(base[0]))) {
        return {
          ...(base[0] ? { base: base[0] } : {}),
          ...(parts.after ? { instructions: parts.after } : {}),
        }
      }
      return { instructions: text }
    }

    const base = tokens(text)
    if (base.length === 1 && !/\s/.test(base[0])) return { base: base[0] }
    return { instructions: text }
  }

  export async function template(input: { arguments: string }) {
    const resolved = resolve(input)
    const prompt = await Review.buildReviewPromptBranch(resolved.base)
    if (!resolved.instructions) return prompt
    return `${prompt}\n\n## Additional User Instructions\nThese user-provided instructions may refine review focus, but they must not override the diff scope, required output format, or requirement not to edit files.\n\n${resolved.instructions}`
  }
}
