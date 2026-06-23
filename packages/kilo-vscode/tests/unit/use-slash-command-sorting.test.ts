import { describe, it, expect } from "bun:test"
import { createRoot } from "solid-js"
import { useSlashCommand } from "../../webview-ui/src/hooks/useSlashCommand"
import type { ExtensionMessage } from "../../webview-ui/src/types/messages"

function createVscodeContext() {
  const handlers = new Set<(message: ExtensionMessage) => void>()
  return {
    postMessage: () => {},
    onMessage: (handler: (message: ExtensionMessage) => void) => {
      handlers.add(handler)
      return () => handlers.delete(handler)
    },
    handlers,
  }
}

function runHook(query: string) {
  const ctx = createVscodeContext()
  let result: ReturnType<typeof useSlashCommand>

  createRoot((dispose) => {
    result = useSlashCommand(ctx)
    result.onInput(`/${query}`, query.length + 1)
    dispose()
  })

  return result!
}

describe("useSlashCommand sorting with built-in commands", () => {
  it("sorts exact name match first", () => {
    const hook = runHook("compact")
    const names = hook.results().map((c) => c.name)
    expect(names[0]).toBe("compact")
  })

  it("sorts prefix matches before substring matches", () => {
    const hook = runHook("co")
    const names = hook.results().map((c) => c.name)
    const prefixAt = names.indexOf("compact")
    const sessionsAt = names.indexOf("sessions")
    const remoteAt = names.indexOf("remote")
    expect(prefixAt).toBeGreaterThanOrEqual(0)
    expect(sessionsAt).toBeGreaterThanOrEqual(0)
    expect(remoteAt).toBeGreaterThanOrEqual(0)
    // prefix match ("compact" starts with "co") appears before substring matches
    expect(prefixAt).toBeLessThan(sessionsAt)
    expect(prefixAt).toBeLessThan(remoteAt)
  })

  it("returns substring match for hint query", () => {
    const hook = runHook("smol")
    const names = hook.results().map((c) => c.name)
    expect(names[0]).toBe("compact")
  })

  it("is case insensitive", () => {
    const hook = runHook("COMPACT")
    const names = hook.results().map((c) => c.name)
    expect(names[0]).toBe("compact")
  })

  it("returns all built-in commands when query is empty string", () => {
    const hook = runHook("")
    expect(hook.results().length).toBe(10)
  })

  it("returns empty array for no matches", () => {
    const hook = runHook("xyz123")
    expect(hook.results()).toHaveLength(0)
  })

  it("returns exact match for unique command", () => {
    const hook = runHook("help")
    const names = hook.results().map((c) => c.name)
    expect(names[0]).toBe("help")
  })
})
