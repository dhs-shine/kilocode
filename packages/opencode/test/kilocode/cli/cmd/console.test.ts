import { afterEach, describe, expect, test } from "bun:test"
import path from "path"
import { explicitNetworkOptions } from "../../../../src/cli/network"
import { Daemon } from "../../../../src/kilocode/daemon/daemon"
import { tmpdir } from "../../../fixture/fixture"

const original = {
  state: process.env.KILO_TEST_DAEMON_STATE_DIR,
  log: process.env.KILO_TEST_DAEMON_LOG_DIR,
  disabled: process.env.KILO_NO_DAEMON,
}

afterEach(async () => {
  await Daemon.stop().catch(() => undefined)
  restore()
})

function restore() {
  process.env.KILO_TEST_DAEMON_STATE_DIR = original.state
  if (original.state === undefined) delete process.env.KILO_TEST_DAEMON_STATE_DIR
  process.env.KILO_TEST_DAEMON_LOG_DIR = original.log
  if (original.log === undefined) delete process.env.KILO_TEST_DAEMON_LOG_DIR
  process.env.KILO_NO_DAEMON = original.disabled
  if (original.disabled === undefined) delete process.env.KILO_NO_DAEMON
}

function dirs(root: string) {
  process.env.KILO_TEST_DAEMON_STATE_DIR = path.join(root, "state")
  process.env.KILO_TEST_DAEMON_LOG_DIR = path.join(root, "log")
  return {
    XDG_DATA_HOME: path.join(root, "xdg-data"),
    XDG_CONFIG_HOME: path.join(root, "xdg-config"),
    XDG_STATE_HOME: path.join(root, "xdg-state"),
    XDG_CACHE_HOME: path.join(root, "xdg-cache"),
  }
}

function temp() {
  return tmpdir({
    dispose: async () => {
      await Daemon.stop().catch(() => undefined)
    },
  })
}

function opts(root: string): Daemon.Options {
  return {
    hostname: "127.0.0.1",
    port: 0,
    mdns: false,
    mdnsDomain: "kilo.local",
    cors: [],
    command: [process.execPath, "--conditions=browser", path.join(process.cwd(), "src/index.ts")],
    env: dirs(root),
    timeout: 20_000,
  }
}

describe("console daemon startup", () => {
  test("detects every explicit network option form", () => {
    expect(
      explicitNetworkOptions([
        "kilo",
        "console",
        "--port=4321",
        "--hostname",
        "0.0.0.0",
        "--no-mdns",
        "--mdns-domain=test.local",
        "--cors",
        "https://example.com",
      ]),
    ).toStrictEqual(["port", "hostname", "mdns", "mdnsDomain", "cors"])
    expect(explicitNetworkOptions(["kilo", "console", "--", "--port=4321"])).toStrictEqual([])
  })

  test("reuses a daemon when explicit options match", async () => {
    await using tmp = await temp()
    const input = opts(tmp.path)
    const first = await Daemon.start(input)
    const state = first.state
    if (!state) throw new Error("Daemon did not provide connection state")

    const daemon = await Daemon.ensure({ ...input, port: state.port }, [
      "port",
      "hostname",
      "mdns",
      "mdnsDomain",
      "cors",
    ])

    expect(Daemon.matches(state, { ...input, port: state.port + 1 }, ["port"])).toBe(false)
    expect(daemon.restarted).toBe(false)
    expect(daemon.result.state?.pid).toBe(state.pid)
  }, 20_000)

  test("treats an explicit auto port as compatible", async () => {
    await using tmp = await temp()
    const input = opts(tmp.path)
    const first = await Daemon.start(input)

    const daemon = await Daemon.ensure(input, ["port"])

    expect(daemon.restarted).toBe(false)
    expect(daemon.result.state?.pid).toBe(first.state?.pid)
  }, 20_000)

  test("supports daemon state written before network options were persisted", async () => {
    await using tmp = await temp()
    const input = opts(tmp.path)
    const first = await Daemon.start(input)
    const state = first.state
    if (!state) throw new Error("Daemon did not provide connection state")
    await Bun.write(Daemon.file(), JSON.stringify({ ...state, options: undefined }))

    expect((await Daemon.read())?.options).toBeUndefined()
    const matched = await Daemon.ensure({ ...input, port: state.port }, ["hostname", "port"])
    expect(matched.restarted).toBe(false)
    expect(matched.result.state?.pid).toBe(state.pid)

    const unknown = await Daemon.ensure(input, ["mdns"])
    expect(unknown.restarted).toBe(true)
    expect(unknown.result.state?.pid).not.toBe(state.pid)
  }, 30_000)

  test("restarts for each mismatched explicit network option", async () => {
    await using tmp = await temp()
    const initial = opts(tmp.path)
    const first = await Daemon.start(initial)
    const state = first.state
    if (!state) throw new Error("Daemon did not provide connection state")

    const host = { ...initial, hostname: "0.0.0.0" }
    const byHost = await Daemon.ensure(host, ["hostname"])
    expect(byHost.restarted).toBe(true)
    expect(byHost.result.state?.pid).not.toBe(state.pid)

    const mdns = { ...host, mdns: true }
    const byMdns = await Daemon.ensure(mdns, ["mdns"])
    expect(byMdns.restarted).toBe(true)
    expect(byMdns.result.state?.pid).not.toBe(byHost.result.state?.pid)

    const loopback = await Daemon.restart({ ...mdns, hostname: "127.0.0.1" })
    const byMdnsHost = await Daemon.ensure(mdns, ["mdns"])
    expect(byMdnsHost.restarted).toBe(true)
    expect(byMdnsHost.result.state?.pid).not.toBe(loopback.state?.pid)

    const domain = { ...mdns, mdnsDomain: "test.local" }
    const byDomain = await Daemon.ensure(domain, ["mdnsDomain"])
    expect(byDomain.restarted).toBe(true)
    expect(byDomain.result.state?.pid).not.toBe(byMdnsHost.result.state?.pid)

    const cors = { ...domain, cors: ["https://example.com"] }
    const byCors = await Daemon.ensure(cors, ["cors"])
    expect(byCors.restarted).toBe(true)
    expect(byCors.result.state?.pid).not.toBe(byDomain.result.state?.pid)
  }, 60_000)
})
