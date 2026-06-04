type URI = {
  path: string
  query: string
}

export function kiloModelFromURI(uri: URI): string | undefined {
  if (uri.path !== "/kilocode/model") return undefined
  return new URLSearchParams(uri.query).get("model") || undefined
}
