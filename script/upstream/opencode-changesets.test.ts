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
      changeset([{ tag_name: "v1.2.2", body: "\r\n## Core\r\n\r\n- Fix issue\r\n" }], {
        from: "1.2.1",
        to: "1.2.2",
        packages: ["@kilocode/cli", "kilo-code"],
        bump: "patch",
        drop: ["Desktop", "SDK"],
      }),
    ).toBe(`---
"@kilocode/cli": patch
"kilo-code": patch
---

Changes from opencode v1.2.1 to v1.2.2 upstream:

## Core

- Fix issue
`)
  })

  test("filters ignored sections and contributor thanks", () => {
    expect(
      changeset([
        {
          tag_name: "v1.2.2",
          body: `## Core

- Keep this

## Desktop

- Drop this

## Misc

- Drop misc

## SDK

- Drop sdk

**Thank you to 1 community contributor:**

- @user:
  - Helped
`,
        },
      ], {
        from: "1.2.1",
        to: "1.2.2",
        packages: ["@kilocode/cli"],
        bump: "patch",
        drop: ["Desktop", "Misc", "SDK"],
      }),
    ).toBe(`---
"@kilocode/cli": patch
---

Changes from opencode v1.2.1 to v1.2.2 upstream:

## Core

- Keep this
`)
  })

  test("bundles release notes into shared sections", () => {
    expect(
      changeset([
        {
          tag_name: "v1.2.1",
          body: `## Core

### Bugfixes

- Fix first

## TUI

### Improvements

- Improve first
`,
        },
        {
          tag_name: "v1.2.2",
          body: `## Core

### Bugfixes

- Fix second

### Improvements

- Improve core

## TUI

### Improvements

- Improve second
`,
        },
      ], {
        from: "1.2.0",
        to: "1.2.2",
        packages: ["@kilocode/cli"],
        bump: "patch",
        drop: ["Desktop", "SDK"],
      }),
    ).toBe(`---
"@kilocode/cli": patch
---

Changes from opencode v1.2.0 to v1.2.2 upstream:

## Core

### Bugfixes

- Fix first
- Fix second

### Improvements

- Improve core

## TUI

### Improvements

- Improve first
- Improve second
`)
  })
})
