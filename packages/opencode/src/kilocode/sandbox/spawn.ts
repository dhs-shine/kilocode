import { generate, available } from "./seatbelt"
import type { Scope } from "./scope"

export interface WrapResult {
  sandboxed: boolean
  command: string
  args: string[]
}

export function wrap(scope: Scope, command: string, args: string[]): WrapResult {
  if (process.platform === "darwin" && available()) {
    const r = generate(scope, command, args)
    return { sandboxed: true, command: r.command, args: r.args }
  }

  return { sandboxed: false, command, args }
}
