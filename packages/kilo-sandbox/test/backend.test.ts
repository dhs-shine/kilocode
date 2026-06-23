import { describe, expect, test } from "bun:test"
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs"
import os from "node:os"
import path from "node:path"
import { Effect } from "effect"
import { backendSupport, prepare, type Launch } from "../src/backend"
import { generate as generateBubblewrap, parseMountinfo } from "../src/bubblewrap"
import { run } from "../src/context"
import type { Profile } from "../src/profile"
import { generate } from "../src/seatbelt"

function makeProfile(): Profile {
  return {
    filesystem: {
      allowWrite: [{ path: "/workspace", kind: "subtree" }],
      denyWrite: [{ path: "/workspace/.git", kind: "subtree" }],
      denyNames: [".git"],
    },
    network: { mode: "deny", allowedHosts: ["example.com"] },
    environment: { deny: ["DROP", "RESET"], set: { KEEP: "profile", RESET: "removed" } },
  }
}

const launch: Launch = {
  command: "/bin/echo",
  args: ["hello"],
  cwd: "/workspace",
  environment: { KEEP: "launch", DROP: "secret" },
}

describe("sandbox launch preparation", () => {
  test("generates a globally overriding overlapping deny policy with parameterized paths", () => {
    const result = generate(makeProfile(), launch)
    const policy = result.args[1]
    expect(policy).toContain('(require-any (literal (param "ALLOW_WRITE_0")) (subpath (param "ALLOW_WRITE_0")))')
    expect(policy).toContain('(require-not (literal (param "DENY_WRITE_0")))')
    expect(policy).toContain('(require-not (subpath (param "DENY_WRITE_0")))')
    expect(policy).toContain('(require-not (regex #"(^|/)\\.git(/|$)"))')
    expect(policy).toContain("(allow file-read*)")
    expect(policy).toContain("(allow network-outbound)")
    expect(policy).toContain("(allow network-inbound)")
    expect(policy).not.toContain("/workspace/.git")
    expect(result.args).toContain("-DALLOW_WRITE_0=/workspace")
    expect(result.args).toContain("-DDENY_WRITE_0=/workspace/.git")
    expect(result.args.slice(-3)).toEqual(["--", "/bin/echo", "hello"])
  })

  test("places shell commands inside the sandbox backend", () => {
    const result = generate(makeProfile(), { ...launch, command: "echo hello", args: [], shell: "/bin/zsh" })
    expect(result.args.slice(-4)).toEqual(["--", "/bin/zsh", "-c", "echo hello"])

    const args = generate(makeProfile(), {
      ...launch,
      command: "printf",
      args: ["%s", "hello world"],
      shell: true,
    })
    expect(args.args.slice(-4)).toEqual(["--", "/bin/sh", "-c", "printf '%s' 'hello world'"])
  })

  test("layers Linux writable roots before protected git metadata without changing the network namespace", () => {
    const root = mkdtempSync(path.join(os.tmpdir(), "kilo-bubblewrap-policy-"))
    const git = path.join(root, ".git")
    mkdirSync(git)
    writeFileSync(path.join(git, "config"), "original")
    const profile: Profile = {
      ...makeProfile(),
      filesystem: {
        allowWrite: [{ path: root, kind: "subtree" }],
        denyWrite: [],
        denyNames: [".git"],
      },
    }

    try {
      const result = generateBubblewrap(profile, { ...launch, cwd: root }, "/opt/kilo/bwrap")
      const writable = result.args.indexOf("--bind")
      const protectedPath = result.args.indexOf("--ro-bind", writable + 1)
      expect(result.command).toBe("/opt/kilo/bwrap")
      expect(writable).toBeGreaterThan(-1)
      expect(protectedPath).toBeGreaterThan(writable)
      expect(result.args.slice(protectedPath, protectedPath + 3)).toEqual(["--ro-bind", git, git])
      expect(result.args).not.toContain("--unshare-net")
      expect(result.args.slice(-3)).toEqual(["--", "/bin/echo", "hello"])
    } finally {
      rmSync(root, { recursive: true, force: true })
    }
  })

  test("parses escaped mount points from Linux mountinfo", () => {
    const content = [
      String.raw`36 25 0:32 / / rw,relatime - overlay overlay rw`,
      String.raw`37 36 0:33 / /tmp/kilo\040root rw - tmpfs tmpfs rw`,
      String.raw`38 37 0:34 / /tmp/kilo\040root/nested\011mount rw - tmpfs tmpfs rw`,
      String.raw`39 36 0:35 / /tmp/back\134slash rw - tmpfs tmpfs rw`,
      "",
    ].join("\n")

    expect(parseMountinfo(content)).toEqual(["/", "/tmp/kilo root", "/tmp/kilo root/nested\tmount", "/tmp/back\\slash"])
  })

  test("allows a mounted writable root but rejects nested mount points", () => {
    const root = mkdtempSync(path.join(os.tmpdir(), "kilo-bubblewrap-mount-"))
    const nested = path.join(root, "nested mount")
    const profile: Profile = {
      ...makeProfile(),
      filesystem: {
        allowWrite: [{ path: root, kind: "subtree" }],
        denyWrite: [],
        denyNames: [],
      },
    }

    try {
      expect(() => generateBubblewrap(profile, launch, "/opt/kilo/bwrap", [root])).not.toThrow()
      expect(() => generateBubblewrap(profile, launch, "/opt/kilo/bwrap", [root, nested])).toThrow(
        `Writable root contains a nested mount point: ${nested}`,
      )
    } finally {
      rmSync(root, { recursive: true, force: true })
    }
  })

  test("rejects a Bubblewrap executable inside a writable root", () => {
    const root = mkdtempSync(path.join(os.tmpdir(), "kilo-bubblewrap-helper-"))
    const helper = path.join(root, "bwrap")
    writeFileSync(helper, "helper")
    const profile: Profile = {
      ...makeProfile(),
      filesystem: {
        allowWrite: [{ path: root, kind: "subtree" }],
        denyWrite: [],
        denyNames: [],
      },
    }

    try {
      expect(() => generateBubblewrap(profile, launch, helper)).toThrow("writable by the sandbox profile")
    } finally {
      rmSync(root, { recursive: true, force: true })
    }
  })

  test("passes the launch through unchanged when no profile is active", async () => {
    const result = await Effect.runPromise(Effect.scoped(prepare(launch)))
    expect(result.command).toBe(launch.command)
    expect(result.args).toBe(launch.args)
    expect(result.cwd).toBe(launch.cwd)
    expect(result.environment).toBe(launch.environment)
  })

  test("merges profile environment values and applies exact deny names", async () => {
    const result = await Effect.runPromise(Effect.scoped(run(makeProfile(), prepare(launch))))
    expect(result.environment?.KEEP).toBe("profile")
    expect(result.environment?.DROP).toBeUndefined()
    expect(result.environment?.RESET).toBeUndefined()
    expect(result.environment?.PATH).toBeUndefined()
  })

  test("reports backend support with a reason when unavailable", () => {
    const support = backendSupport()
    expect(typeof support.available).toBe("boolean")
    if (!support.available) expect(support.reason?.length).toBeGreaterThan(0)
  })
})
