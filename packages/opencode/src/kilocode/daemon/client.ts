import * as Log from "@opencode-ai/core/util/log"
import { Daemon } from "./daemon"

export namespace DaemonClient {
  const log = Log.create({ service: "daemon.client" })

  export type Connection = {
    url: string
    headers: Record<string, string>
    state: Daemon.State
  }

  export function enabled() {
    return !process.env.KILO_NO_DAEMON
  }

  export function options(input: Partial<Daemon.Options> = {}): Daemon.Options {
    return {
      hostname: "127.0.0.1",
      port: 0,
      mdns: false,
      mdnsDomain: "kilo.local",
      cors: ["http://127.0.0.1:3017", "http://localhost:3017"],
      ...input,
    }
  }

  export function headers(state: Daemon.State) {
    return { Authorization: `Basic ${state.token}` }
  }

  export async function connect(input: Daemon.Options): Promise<Connection | undefined> {
    if (!enabled()) return undefined
    const daemon = await Daemon.start(input)
    if (!daemon.running || !daemon.state) throw new Error(daemon.reason ?? "Daemon did not start")
    return {
      url: daemon.state.url,
      headers: headers(daemon.state),
      state: daemon.state,
    }
  }

  export async function maybe(input: Daemon.Options): Promise<Connection | undefined> {
    return await connect(input).catch((err) => {
      log.warn("daemon unavailable, falling back to embedded server", { err })
      return undefined
    })
  }
}
