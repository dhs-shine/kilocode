import { describe, expect, test } from "bun:test"

import { format, payload } from "../../../src/kilocode/cli/cmd/balance"

describe("balance CLI formatting", () => {
  test("formats personal balance for human output", () => {
    expect(
      format({
        email: "one@example.com",
        team: "Personal",
        organizationId: null,
        balance: 12.345,
      }),
    ).toBe("Account: one@example.com\nTeam: Personal\nBalance: $12.35")
  })

  test("formats team balance for human output", () => {
    expect(
      format({
        email: "one@example.com",
        team: "Team One",
        organizationId: "org-1",
        balance: 7,
      }),
    ).toBe("Account: one@example.com\nTeam: Team One\nBalance: $7.00")
  })

  test("creates JSON payload", () => {
    expect(
      payload({
        profile: {
          email: "one@example.com",
          organizations: [{ id: "org-1", name: "Team One", role: "admin" }],
        },
        balance: { balance: 3.5 },
        organizationId: "org-1",
      }),
    ).toEqual({
      email: "one@example.com",
      team: "Team One",
      organizationId: "org-1",
      balance: 3.5,
    })
  })
})
