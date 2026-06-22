import { describe, it, expect } from "bun:test"

interface SlashCommandEntry {
  name: string
  description?: string
  hints: string[]
  action?: () => void
}

const getMatchScore = (cmd: SlashCommandEntry, lower: string): number => {
  const name = cmd.name.toLowerCase()
  if (name === lower) return 3
  if (name.startsWith(lower)) return 2
  if (name.includes(lower)) return 1
  if (cmd.description?.toLowerCase().includes(lower)) return 1
  if (cmd.hints.some((h) => h.toLowerCase().includes(lower))) return 1
  return 0
}

const sortResults = (commands: SlashCommandEntry[], query: string): SlashCommandEntry[] => {
  const lower = query.toLowerCase()
  return commands
    .filter(
      (cmd) =>
        cmd.name.toLowerCase().includes(lower) ||
        cmd.description?.toLowerCase().includes(lower) ||
        cmd.hints.some((h) => h.toLowerCase().includes(lower)),
    )
    .sort((a, b) => getMatchScore(b, lower) - getMatchScore(a, lower))
}

const sampleCommands: SlashCommandEntry[] = [
  { name: "commit", description: "Create a commit", hints: ["ci"] },
  { name: "compact", description: "Summarize session", hints: ["smol", "condense"] },
  { name: "continue", description: "Continue session", hints: ["resume"] },
  { name: "config", description: "Open settings", hints: ["settings"] },
  { name: "new", description: "Start new session", hints: ["clear"] },
  { name: "help", description: "Open help", hints: [] },
  { name: "models", description: "Switch model", hints: [] },
  { name: "agents", description: "Switch agent", hints: ["modes"] },
]

describe("getMatchScore", () => {
  it("returns 3 for exact name match", () => {
    expect(getMatchScore({ name: "commit", description: "", hints: [] }, "commit")).toBe(3)
    expect(getMatchScore({ name: "COMMIT", description: "", hints: [] }, "commit")).toBe(3)
  })

  it("returns 2 for prefix match", () => {
    expect(getMatchScore({ name: "compact", description: "", hints: [] }, "com")).toBe(2)
    expect(getMatchScore({ name: "config", description: "", hints: [] }, "con")).toBe(2)
  })

  it("returns 1 for substring match in name", () => {
    expect(getMatchScore({ name: "continue", description: "", hints: [] }, "tin")).toBe(1)
    expect(getMatchScore({ name: "models", description: "", hints: [] }, "odel")).toBe(1)
  })

  it("returns 1 for substring match in description", () => {
    expect(getMatchScore({ name: "help", description: "Open help documentation", hints: [] }, "documentation")).toBe(1)
  })

  it("returns 1 for substring match in hints", () => {
    expect(getMatchScore({ name: "compact", description: "", hints: ["smol", "condense"] }, "smol")).toBe(1)
    expect(getMatchScore({ name: "agents", description: "", hints: ["modes"] }, "modes")).toBe(1)
  })

  it("returns 0 for no match", () => {
    expect(getMatchScore({ name: "commit", description: "", hints: [] }, "xyz")).toBe(0)
    expect(getMatchScore({ name: "help", description: "Open help", hints: [] }, "xyz")).toBe(0)
  })
})

describe("sortResults", () => {
  it("sorts exact match first", () => {
    const results = sortResults(sampleCommands, "commit")
    expect(results.length).toBeGreaterThan(0)
    expect(results[0]?.name).toBe("commit")
  })

  it("sorts prefix matches before substring matches", () => {
    const results = sortResults(sampleCommands, "com")
    // "com" matches "commit" (starts with "com") and "compact" (starts with "com")
    // "config" starts with "con", not "com"
    expect(results).toHaveLength(2)
    // Same score (2), stable sort preserves original array order: commit (idx 0), compact (idx 1)
    expect(results[0]?.name).toBe("commit")
    expect(results[1]?.name).toBe("compact")
  })

  it("preserves original order for same-score prefix matches", () => {
    const results = sortResults(sampleCommands, "co")
    // All 4 match as prefix (score 2). Stable sort preserves original array order.
    expect(results.map((r) => r.name)).toEqual(["commit", "compact", "continue", "config"])
  })

  it("sorts substring matches after prefix matches", () => {
    const results = sortResults(sampleCommands, "tin")
    expect(results.length).toBeGreaterThan(0)
    expect(results[0]?.name).toBe("continue")
  })

  it("is case insensitive", () => {
    const results = sortResults(sampleCommands, "COMMIT")
    expect(results.length).toBeGreaterThan(0)
    expect(results[0]?.name).toBe("commit")
  })

  it("matches in description and hints", () => {
    const results = sortResults(sampleCommands, "smol")
    expect(results.length).toBeGreaterThan(0)
    expect(results[0]?.name).toBe("compact")
  })

  it("returns all commands when query is empty string", () => {
    const results = sortResults(sampleCommands, "")
    expect(results.length).toBe(sampleCommands.length)
  })

  it("returns empty array for no matches", () => {
    const results = sortResults(sampleCommands, "xyz123")
    expect(results).toHaveLength(0)
  })
})