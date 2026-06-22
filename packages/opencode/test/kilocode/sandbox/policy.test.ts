import { describe, expect, test } from "bun:test"
import { Config } from "@/config/config"
import { profile } from "@/kilocode/sandbox/policy"
import type { InstanceContext } from "@/project/instance-context"
import { ProjectID } from "@/project/schema"

const ctx: InstanceContext = {
  directory: "/workspace/project",
  worktree: "/workspace",
  project: {
    id: ProjectID.make("sandbox-policy-test"),
    worktree: "/workspace",
    time: { created: 0, updated: 0 },
    sandboxes: [],
  },
}

function config(permission: NonNullable<Config.Info["permission"]>): Config.Info {
  return { permission }
}

function paths(input: ReturnType<typeof profile>, key: "allowWrite" | "denyWrite") {
  return input.filesystem[key].map((rule) => rule.path)
}

function rules(input: ReturnType<typeof profile>) {
  return input.filesystem.writeRules
}

describe("sandbox policy", () => {
  test("keeps project roots writable when external directories are denied", () => {
    const result = profile(ctx, config({ external_directory: "deny" }))
    expect(paths(result, "allowWrite")).toContain(ctx.directory)
    expect(paths(result, "denyWrite")).not.toContain("/")
  })

  test("turns a later ask rule into an exclusion from an earlier allow", () => {
    const result = profile(
      ctx,
      config({
        external_directory: {
          "/tmp/*": "allow",
          "/tmp/private/*": "ask",
        },
      }),
    )
    expect(rules(result)).toEqual([
      { action: "allow", rule: { path: "/tmp", kind: "subtree" } },
      { action: "ask", rule: { path: "/tmp/private", kind: "subtree" } },
    ])
  })

  test("allows a later narrow rule after an earlier deny", () => {
    const result = profile(
      ctx,
      config({
        external_directory: {
          "/tmp/*": "deny",
          "/tmp/public/*": "allow",
        },
      }),
    )
    expect(rules(result)).toEqual([
      { action: "deny", rule: { path: "/tmp", kind: "subtree" } },
      { action: "allow", rule: { path: "/tmp/public", kind: "subtree" } },
    ])
  })

  test("preserves exact paths and supports trailing double-star directory rules", () => {
    const result = profile(
      ctx,
      config({
        external_directory: {
          "/tmp/exact": "allow",
          "/var/cache/**": "allow",
        },
      }),
    )
    expect(rules(result)).toEqual([
      { action: "allow", rule: { path: "/tmp/exact", kind: "literal" } },
      { action: "allow", rule: { path: "/var/cache", kind: "subtree" } },
    ])
  })

  test("hard-protects git metadata under every current and future writable root", () => {
    const result = profile(ctx, config({ external_directory: "allow" }))
    expect(result.filesystem.denyNames).toContain(".git")
    expect(rules(result)).toContainEqual({ action: "allow", rule: { path: "/", kind: "subtree" } })
  })
})
