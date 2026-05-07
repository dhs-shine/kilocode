import { createKiloClient, type KiloClient } from "@kilocode/sdk/v2"
import { UI } from "@/cli/ui"
import { DaemonClient } from "@/kilocode/daemon/client"
import { isBuiltinCommand, type BuiltinCommand } from "@/kilocode/session/builtin-commands"
import { Provider } from "@/provider/provider"
import { Filesystem } from "@/util/filesystem"

export namespace KiloRun {
  export const isBuiltin = isBuiltinCommand

  export function validateBuiltin(args: { command?: string; continue?: boolean; session?: string }) {
    if (!isBuiltin(args.command)) return
    if (args.continue || args.session) return
    UI.error(`--command ${args.command} requires --continue or --session`)
    process.exit(1)
  }

  export async function runBuiltin(
    sdk: KiloClient,
    sessionID: string,
    command: BuiltinCommand,
    model?: string,
    directory?: string,
  ) {
    const selected = await resolve(sdk, model)
    if (!selected) {
      UI.error("No model specified and no default provider configured")
      process.exit(1)
    }

    switch (command) {
      case "compact":
      case "summarize":
        return sdk.session.summarize({
          sessionID,
          directory,
          providerID: selected.providerID,
          modelID: selected.modelID,
        })
    }
  }
}

export namespace KiloRunDaemon {
  export type Input = {
    directory?: string
    execute: (client: KiloClient) => Promise<void>
  }

  export async function attach(input: Input) {
    const daemon = await DaemonClient.maybe()
    if (!daemon) return false
    const dir = input.directory ?? Filesystem.resolve(process.cwd())
    const client = createKiloClient({ baseUrl: daemon.url, directory: dir, headers: daemon.headers })
    await input.execute(client)
    return true
  }
}

async function resolve(sdk: KiloClient, model?: string) {
  if (model) {
    const parsed = Provider.parseModel(model)
    return { providerID: parsed.providerID, modelID: parsed.modelID }
  }
  const result = await sdk.config.providers()
  const defaults = result.data?.default ?? {}
  const providerID = Object.keys(defaults)[0]
  if (!providerID) return undefined
  return { providerID, modelID: defaults[providerID] }
}
