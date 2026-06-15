import { describe, expect, test } from "bun:test"

import { changeset, select, type Release } from "./opencode-changesets"

const releases: Release[] = [
  { tag_name: "v1.2.3", body: "Patch release" },
  { tag_name: "v1.2.2", body: "\r\n## Core\r\n\r\n- Fix issue\r\n" },
  { tag_name: "1.2.1", body: "Old tag without prefix" },
  { tag_name: "v1.2.0", body: "Base release" },
  { tag_name: "v1.2.4", body: "Draft release", draft: true },
  { tag_name: "v1.2.5", body: "Prerelease", prerelease: true },
]

describe("opencode changesets", () => {
  test("selects releases in semver range with normalized tags", () => {
    expect(select(releases, "1.2.0", "v1.2.3")).toEqual([
      { tag_name: "v1.2.1", body: "Old tag without prefix" },
      { tag_name: "v1.2.2", body: "\r\n## Core\r\n\r\n- Fix issue\r\n" },
      { tag_name: "v1.2.3", body: "Patch release" },
    ])
  })

  test("can include prereleases", () => {
    expect(select(releases, "1.2.3", "1.2.5", true).map((release) => release.tag_name)).toEqual(["v1.2.5"])
  })

  test("formats changeset markdown", () => {
    expect(
      changeset({ tag_name: "v1.2.2", body: "\r\n## Core\r\n\r\n- Fix issue\r\n" }, {
        packages: ["@kilocode/cli", "kilo-code"],
        bump: "patch",
      }),
    ).toBe(`---
"@kilocode/cli": patch
"kilo-code": patch
---

Integrate upstream opencode v1.2.2 release notes.

## Core

- Fix issue
`)
  })
})
