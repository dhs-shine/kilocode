import { test, expect, describe } from "bun:test"
import { generate } from "@/kilocode/sandbox/seatbelt"
import type { Scope } from "@/kilocode/sandbox/scope"

function makeScope(overrides: Partial<Scope> = {}): Scope {
  return {
    writableRoots: [],
    ...overrides,
  }
}

describe("seatbelt.generate", () => {
  test("produces sandbox-exec command with -p flag", () => {
    const result = generate(makeScope(), "/bin/echo", ["hello"])
    expect(result.command).toBe("/usr/bin/sandbox-exec")
    expect(result.args[0]).toBe("-p")
    expect(typeof result.args[1]).toBe("string")
  })

  test("ends with -- and the original command", () => {
    const result = generate(makeScope(), "/bin/echo", ["hello", "world"])
    const tail = result.args.slice(-4)
    expect(tail).toEqual(["--", "/bin/echo", "hello", "world"])
  })

  test("profile starts with version 1 and deny default", () => {
    const result = generate(makeScope(), "/bin/echo", [])
    const policy = result.args[1]
    expect(policy).toContain("(version 1)")
    expect(policy).toContain("(deny default)")
  })

  test("reads are always allowed (file-level sandbox confines writes only)", () => {
    const result = generate(makeScope(), "/bin/echo", [])
    expect(result.args[1]).toContain("(allow file-read*)")
  })

  test("writable root without exclusions produces a require-all subpath", () => {
    const result = generate(
      makeScope({
        writableRoots: [{ path: "/private/tmp/proj", readonlySubpaths: [] }],
      }),
      "/bin/echo",
      [],
    )
    const policy = result.args[1]
    expect(policy).toContain('(subpath (param "WRITABLE_ROOT_0"))')
    expect(result.args).toContain("-DWRITABLE_ROOT_0=/private/tmp/proj")
  })

  test("writable root with exclusions produces require-all with require-not", () => {
    const result = generate(
      makeScope({
        writableRoots: [
          {
            path: "/private/tmp/proj",
            readonlySubpaths: ["/private/tmp/proj/.git"],
          },
        ],
      }),
      "/bin/echo",
      [],
    )
    const policy = result.args[1]
    expect(policy).toContain("(require-all")
    expect(policy).toContain('(subpath (param "WRITABLE_ROOT_0"))')
    expect(policy).toContain('(require-not (literal (param "WRITABLE_ROOT_0_EXCL_0")))')
    expect(policy).toContain('(require-not (subpath (param "WRITABLE_ROOT_0_EXCL_0")))')
    expect(result.args).toContain("-DWRITABLE_ROOT_0_EXCL_0=/private/tmp/proj/.git")
  })

  test("adds regex denies for protected metadata not already in exclusions", () => {
    const result = generate(
      makeScope({
        writableRoots: [
          {
            path: "/private/tmp/proj",
            readonlySubpaths: [],
          },
        ],
      }),
      "/bin/echo",
      [],
    )
    // .git is not in exclusions, so it gets a regex deny
    expect(result.args[1]).toContain("\\.git")
  })

  test("protected metadata already in exclusions is not duplicated as regex", () => {
    const result = generate(
      makeScope({
        writableRoots: [
          {
            path: "/private/tmp/proj",
            readonlySubpaths: ["/private/tmp/proj/.git"],
          },
        ],
      }),
      "/bin/echo",
      [],
    )
    const policy = result.args[1]
    expect(policy).not.toContain("\\.git")
  })

  test("network is always allowed (file-level sandbox does not confine network)", () => {
    const result = generate(makeScope(), "/bin/echo", [])
    const policy = result.args[1]
    expect(policy).toContain("(allow network-outbound)")
    expect(policy).toContain("(allow network-inbound)")
    expect(policy).toContain("com.apple.trustd.agent")
    expect(policy).toContain("com.apple.networkd")
  })

  test("multiple writable roots get indexed params", () => {
    const result = generate(
      makeScope({
        writableRoots: [
          { path: "/private/tmp/proj", readonlySubpaths: [] },
          { path: "/private/tmp/kilo", readonlySubpaths: [] },
        ],
      }),
      "/bin/echo",
      [],
    )
    expect(result.args).toContain("-DWRITABLE_ROOT_0=/private/tmp/proj")
    expect(result.args).toContain("-DWRITABLE_ROOT_1=/private/tmp/kilo")
    const policy = result.args[1]
    expect(policy).toContain('(subpath (param "WRITABLE_ROOT_0"))')
    expect(policy).toContain('(subpath (param "WRITABLE_ROOT_1"))')
  })

  test("no writable roots produces no write policy", () => {
    const result = generate(makeScope({ writableRoots: [] }), "/bin/echo", [])
    const policy = result.args[1]
    expect(policy).toContain("(allow file-read*)")
    expect(policy).not.toContain("(allow file-write*")
  })
})
