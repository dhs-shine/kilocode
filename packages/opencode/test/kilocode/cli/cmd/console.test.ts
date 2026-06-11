import { afterEach, describe, expect, test } from "bun:test"
import path from "path"
import { startDaemon } from "../../../../src/kilocode/cli/cmd/console"
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
  test("reuses a daemon when the requested host and port match", async () => {
    await using tmp = await tmpdir()
    const env = opts(tmp.path)

    const first = await startDaemon(env)
    const reused = await startDaemon({ ...env, port: first.port })

    expect(reused.pid).toBe(first.pid)
  }, 20_000)

  test("does not restart when the requested port is the default auto port", async () => {
    await using tmp = await tmpdir()
    const env = opts(tmp.path)

    const first = await startDaemon(env)
    const reused = await startDaemon({ ...env, port: 0 })

    expect(reused.pid).toBe(first.pid)
  }, 20_000)

  test("restarts a reused daemon when the requested port differs", async () => {
    await using tmp = await tmpdir()
    const env = opts(tmp.path)
    const first = await startDaemon(env)

    const restarted = await startDaemon({ ...env, port: 0 }, true)

    expect(restarted.pid).not.toBe(first.pid)
  }, 20_000)
})
