export type Path = "/projects" | "/profile" | "/settings"

export type Project = {
  id: string
  name: string
  label: string
  path: string
}

export const projects: Project[] = [
  { id: "cli", name: "Kilo CLI", label: "KC", path: "/projects" },
  { id: "vscode", name: "VS Code", label: "VS", path: "/projects" },
  { id: "gateway", name: "Gateway", label: "GW", path: "/projects" },
]

export function path(input: string): Path {
  if (input === "/profile") return "/profile"
  if (input.startsWith("/settings") || input.startsWith("/config") || input.includes("/settings")) return "/settings"
  return "/projects"
}
