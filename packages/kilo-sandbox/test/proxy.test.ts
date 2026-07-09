import { afterEach, describe, expect, test } from "bun:test"
import { lstat } from "node:fs/promises"
import { connect } from "node:net"
import { startProxy, type ProxyResolver } from "../src/proxy"

const close: Array<() => Promise<void> | void> = []
const posix = process.platform === "win32" ? test.skip : test

afterEach(async () => {
  await Promise.all(close.splice(0).map((dispose) => dispose()))
})

function upstream() {
  const server = Bun.serve({
    hostname: "127.0.0.1",
    port: 0,
    fetch(request) {
      return new Response(new URL(request.url).pathname)
    },
  })
  close.push(() => server.stop(true))
  return server
}

function resolver(port: number, calls: string[]): ProxyResolver {
  return async (dest) => {
    calls.push(dest.authority)
    if (dest.port !== port) throw new Error("unexpected port")
    return { address: "127.0.0.1", family: 4 as const }
  }
}

describe("sandbox trusted proxy", () => {
  test("allows only authenticated exact destinations", async () => {
    const target = upstream()
    const port = target.port!
    const calls: string[] = []
    const proxy = await startProxy([`allowed.test:${port}`], "darwin", resolver(port, calls))
    close.push(proxy.close)

    const allowed = await fetch(`http://allowed.test:${port}/allowed`, { proxy: proxy.url })
    const denied = await fetch(`http://blocked.allowed.test:${port}/blocked`, { proxy: proxy.url })
    const unauthenticated = await fetch(`http://allowed.test:${port}/unauthenticated`, {
      proxy: proxy.url.replace(/kilo:[^@]+@/, ""),
    })

    expect(allowed.status).toBe(200)
    expect(await allowed.text()).toBe("/allowed")
    expect(denied.status).toBe(403)
    expect(unauthenticated.status).toBe(407)
    expect(calls).toEqual([`allowed.test:${port}`])
  })

  test("filters CONNECT before opening a tunnel", async () => {
    const target = Bun.listen({
      hostname: "127.0.0.1",
      port: 0,
      socket: {
        data(socket, data) {
          socket.write(data)
        },
      },
    })
    close.push(() => target.stop(true))
    const calls: string[] = []
    const proxy = await startProxy([`allowed.test:${target.port}`], "darwin", resolver(target.port, calls))
    close.push(proxy.close)
    const auth = Buffer.from(`kilo:${proxy.token}`).toString("base64")

    const response = await new Promise<string>((resolve, reject) => {
      const socket = connect(proxy.port!, "127.0.0.1")
      let data = ""
      socket.on("connect", () =>
        socket.write(
          `CONNECT allowed.test:${target.port} HTTP/1.1\r\nHost: allowed.test:${target.port}\r\nProxy-Authorization: Basic ${auth}\r\n\r\n`,
        ),
      )
      socket.on("data", (chunk) => {
        data += chunk.toString()
        if (!data.includes("200 Connection Established")) return
        socket.end()
        resolve(data)
      })
      socket.on("error", reject)
    })

    expect(response).toContain("200 Connection Established")
    expect(calls).toEqual([`allowed.test:${target.port}`])
  })

  test("rechecks redirect destinations without resolving denied hosts", async () => {
    let requests = 0
    const target = Bun.serve({
      hostname: "127.0.0.1",
      port: 0,
      fetch(request) {
        requests++
        return Response.redirect(`http://blocked.test:${new URL(request.url).port}/exfiltrate`, 302)
      },
    })
    close.push(() => target.stop(true))
    const port = target.port!
    const calls: string[] = []
    const proxy = await startProxy([`allowed.test:${port}`], "darwin", resolver(port, calls))
    close.push(proxy.close)

    const response = await fetch(`http://allowed.test:${port}/redirect`, { proxy: proxy.url })
    expect(response.status).toBe(403)
    expect(requests).toBe(1)
    expect(calls).toEqual([`allowed.test:${port}`])
  })

  posix("creates a private Unix listener for Linux relay mode", async () => {
    const target = upstream()
    const port = target.port!
    const proxy = await startProxy([`allowed.test:${port}`], "linux", resolver(port, []))
    close.push(proxy.close)
    expect(proxy.socket).toContain("kilo-sandbox-proxy-")
    expect(proxy.port).toBeGreaterThan(0)
    expect((await lstat(proxy.socket!)).isSocket()).toBe(true)
  })
})
