import { describe, expect, test } from "bun:test"
import { resolveProfileOrganization, type KiloAuth } from "../../src/server/handlers.js"
import type { KilocodeProfile } from "../../src/types.js"

const oauth = (input: Partial<Extract<KiloAuth, { type: "oauth" }>> = {}): Extract<KiloAuth, { type: "oauth" }> => ({
  type: "oauth",
  access: "token",
  refresh: "token",
  expires: 1,
  ...input,
})

const profile = (input: Partial<KilocodeProfile> = {}): KilocodeProfile => ({
  email: "user@example.com",
  organizations: [{ id: "org_1", name: "Acme", role: "MEMBER" }],
  ...input,
})

describe("resolveProfileOrganization", () => {
  test("uses cloud selected organization only when no local selection exists", () => {
    expect(resolveProfileOrganization(profile({ selectedOrganizationId: "org_1" }), oauth())).toEqual({
      currentOrgId: "org_1",
      persistOrgId: "org_1",
    })
  })

  test("preserves a manual organization selection over the cloud default", () => {
    expect(
      resolveProfileOrganization(
        profile({
          selectedOrganizationId: "org_1",
          organizations: [
            { id: "org_1", name: "Acme", role: "MEMBER" },
            { id: "org_2", name: "Beta", role: "MEMBER" },
          ],
        }),
        oauth({ accountId: "org_2", accountSelection: "manual" }),
      ),
    ).toEqual({ currentOrgId: "org_2", persistOrgId: null })
  })

  test("preserves explicit personal selection when personal is available", () => {
    expect(
      resolveProfileOrganization(
        profile({ selectedOrganizationId: "org_1", hasPersonalAccount: true }),
        oauth({ accountSelection: "manual" }),
      ),
    ).toEqual({ currentOrgId: null, persistOrgId: null })
  })

  test("uses the first organization when personal is unavailable and no valid cloud selection exists", () => {
    expect(
      resolveProfileOrganization(
        profile({ selectedOrganizationId: "missing", hasPersonalAccount: false }),
        oauth({ accountSelection: "manual" }),
      ),
    ).toEqual({ currentOrgId: "org_1", persistOrgId: "org_1" })
  })
})
