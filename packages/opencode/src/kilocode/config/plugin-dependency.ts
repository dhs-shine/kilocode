import { InstallationBuildKind, InstallationVersion } from "@opencode-ai/core/installation/version"

export namespace KilocodePluginDependency {
  export function version(opts?: { kind?: "source" | "release"; version?: string }) {
    const kind = opts?.kind ?? InstallationBuildKind
    if (kind !== "release") return undefined
    return opts?.version ?? InstallationVersion
  }
}
