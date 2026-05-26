export type Path = "/projects" | "/profile" | "/settings"

export function path(input: string): Path {
  if (input === "/profile") return "/profile"
  if (input.startsWith("/settings") || input.startsWith("/config") || input.includes("/settings")) return "/settings"
  return "/projects"
}
