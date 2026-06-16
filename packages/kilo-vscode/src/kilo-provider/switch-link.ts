const paths = new Set(["/kilocode/switch", "/kilocode/model"])

export function parseSwitchLink(path: string, query: string) {
  if (!paths.has(path)) return

  const params = new URLSearchParams(query)
  const modelID = params.get("model")
  if (!modelID) return

  const agent = params.get("agent") || params.get("mode") || undefined
  return { modelID, ...(agent && { agent }) }
}
