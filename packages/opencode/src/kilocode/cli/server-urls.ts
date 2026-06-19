// kilocode_change - new file
import { isIP } from "net"
import { networkInterfaces } from "os"

export function getNetworkIPs() {
  const nets = networkInterfaces()
  const results: string[] = []

  for (const name of Object.keys(nets)) {
    const net = nets[name]
    if (!net) continue
    for (const netInfo of net) {
      if (netInfo.internal || netInfo.family !== "IPv4") continue
      results.push(netInfo.address)
    }
  }

  return results
}

function format(hostname: string, port: number) {
  const url = new URL("http://localhost")
  url.hostname = isIP(hostname) === 6 ? `[${hostname}]` : hostname
  url.port = String(port)
  return url.origin
}

export function serverUrls(hostname: string, port: number) {
  const bind = format(hostname, port)
  const wildcard = hostname === "0.0.0.0" || hostname === "::"
  const local = wildcard ? format("localhost", port) : bind
  const ip = wildcard ? getNetworkIPs()[0] : undefined

  return {
    local,
    network: ip ? format(ip, port) : undefined,
    bind,
  }
}
