import { describe, expect, test } from "bun:test"
import { InstallationBuildKind, InstallationVersion } from "@opencode-ai/core/installation/version"
import { KilocodePluginDependency } from "@/kilocode/config/plugin-dependency"

describe("KilocodePluginDependency", () => {
  test("uses installation defaults when options are omitted", () => {
    expect(KilocodePluginDependency.version()).toBe(
      KilocodePluginDependency.version({ kind: InstallationBuildKind, version: InstallationVersion }),
    )
  })

  test("pins plugin dependency for release builds", () => {
    expect(KilocodePluginDependency.version({ kind: "release", version: "7.3.54" })).toBe("7.3.54")
  })

  test("does not pin plugin dependency for source builds", () => {
    expect(KilocodePluginDependency.version({ kind: "source", version: "0.0.0-maple-squirrel-202606251622" })).toBeUndefined()
  })

  test("does not pin plugin dependency for source builds without a version", () => {
    expect(KilocodePluginDependency.version({ kind: "source" })).toBeUndefined()
  })
})
