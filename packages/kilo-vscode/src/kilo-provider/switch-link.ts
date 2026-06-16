const paths = new Set(["/kilocode/switch", "/kilocode/model"])

export function parseSwitchLink(path: string, query: string) {
  if (!paths.has(path)) return

  const params = new URLSearchParams(query)
  const modelID = params.get("model") || undefined
  const agent = params.get("agent") || params.get("mode") || undefined
  if (!modelID && !agent) return

  return { ...(modelID && { modelID }), ...(agent && { agent }) }
}
