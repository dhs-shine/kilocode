import { describe, expect, it } from "bun:test"
import { planInsertion } from "../../src/services/autocomplete/next-edit/pendingEdit"

describe("planInsertion", () => {
  it("appends after the final unterminated line at EOF", () => {
    const edit = planInsertion(
      { diffStartLine: 2, replacement: "third\n" },
      { lineCount: 2, end: (line) => [5, 6][line] },
    )

    expect(edit).toEqual({ line: 1, character: 6, text: "\nthird" })
  })

  it("keeps insertion-before-line semantics for a trailing empty line", () => {
    const edit = planInsertion(
      { diffStartLine: 1, replacement: "second\n" },
      { lineCount: 2, end: (line) => [5, 0][line] },
    )

    expect(edit).toEqual({ line: 1, character: 0, text: "second\n" })
  })
})
