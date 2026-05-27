export type Path = "/projects" | "/project" | "/profile" | "/settings"

export function path(input: string): Path {
  if (input === "/profile") return "/profile"
  if (input.startsWith("/settings") || input.startsWith("/config")) return "/settings"
  if (input.startsWith("/projects/")) return "/project"
  return "/projects"
}
