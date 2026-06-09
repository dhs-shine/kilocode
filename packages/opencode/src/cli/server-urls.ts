import { networkInterfaces } from "os"

export function getNetworkIPs() {
  const nets = networkInterfaces()
  const results: string[] = []

  for (const name of Object.keys(nets)) {
    const net = nets[name]
    if (!net) continue
    for (const netInfo of net) {
      if (netInfo.internal || netInfo.family !== "IPv4") continue
      if (netInfo.address.startsWith("172.")) continue
      results.push(netInfo.address)
    }
  }

  return results
}

export function serverUrls(hostname: string, port: number) {
  const local = `http://localhost:${port}`
  const bindStr = `http://${hostname}:${port}`

  const isLoopback = hostname === "127.0.0.1" || hostname === "localhost" || hostname === "::1"
  const ips = getNetworkIPs()
  const lanUrl = ips.length > 0 ? `http://${ips[0]}:${port}` : undefined

  return {
    local,
    network: isLoopback ? lanUrl : bindStr,
    bind: bindStr,
  }
}
