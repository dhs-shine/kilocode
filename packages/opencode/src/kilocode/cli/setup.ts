import type { Argv } from "yargs"
import { Global } from "@opencode-ai/core/global"
import { InstallationBuildKind, InstallationVersion } from "@opencode-ai/core/installation/version"
import { Telemetry } from "@kilocode/kilo-telemetry"
import { migrateLegacyKiloAuth, ENV_FEATURE, ENV_VERSION } from "@kilocode/kilo-gateway"
import { AppRuntime } from "@/effect/app-runtime"
import { Config } from "@/config/config"
import { Auth } from "@/auth"
import { InstanceRuntime } from "@/project/instance-runtime"
import { SessionExport } from "@/kilocode/session-export"
import { createHelpCommand } from "@/kilocode/help-command"
import { KiloConsoleCommand } from "@/kilocode/cli/cmd/console"
import { RollCallCommand } from "@/kilocode/cli/cmd/roll-call"
import { ProfileCommand } from "@/kilocode/cli/cmd/profile"
import { DaemonCommand } from "@/kilocode/cli/cmd/daemon"
import { DevSetupCommand, DevAliasCommand } from "@/kilocode/cli/dev-setup"
import { RemoteCommand } from "@/cli/cmd/remote"
import { ConfigCommand as ConfigCLICommand } from "@/cli/cmd/config"

export namespace KiloCli {
  export function register<T>(cli: Argv<T>): Argv<T> {
    cli
      .command(KiloConsoleCommand)
      .command(RollCallCommand)
      .command(ProfileCommand)
      .command(RemoteCommand)
      .command(DaemonCommand)
      .command(ConfigCLICommand)
    if (InstallationBuildKind !== "release") cli.command(DevSetupCommand).command(DevAliasCommand)
    cli.command(createHelpCommand(() => cli))
    return cli
  }

  export async function bootstrap(): Promise<void> {
    if (!process.env[ENV_FEATURE]) process.env[ENV_FEATURE] = process.argv.includes("serve") ? "unknown" : "cli"
    if (!process.env[ENV_VERSION]) process.env[ENV_VERSION] = InstallationVersion
    process.env.KILO = "1"

    const cfg = await AppRuntime.runPromise(Config.Service.use((svc) => svc.getGlobal()))
    await Telemetry.init({
      dataPath: Global.Path.data,
      version: InstallationVersion,
      enabled: cfg.experimental?.openTelemetry !== false,
    })

    await migrateLegacyKiloAuth(
      async () => (await AppRuntime.runPromise(Auth.Service.use((svc) => svc.get("kilo")))) !== undefined,
      async (auth) => AppRuntime.runPromise(Auth.Service.use((svc) => svc.set("kilo", auth))),
    )

    const auth = await AppRuntime.runPromise(Auth.Service.use((svc) => svc.get("kilo")))
    if (auth) {
      const token = auth.type === "oauth" ? auth.access : auth.key
      const account = auth.type === "oauth" ? auth.accountId : undefined
      await Telemetry.updateIdentity(token, account)
    }

    Telemetry.trackCliStart()
  }

  export async function shutdown(): Promise<void> {
    const code = typeof process.exitCode === "number" ? process.exitCode : undefined
    Telemetry.trackCliExit(code)
    await SessionExport.shutdown()
    await Telemetry.shutdown()
    await InstanceRuntime.disposeAllInstances()
  }
}
