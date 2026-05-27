import type { Component } from "solid-js"
import type { IconProps } from "@kilocode/kilo-web-ui/icon"
import { AgentBuilderRoute, AgentsRoute } from "./AgentsRoute"
import { CliUiRoute } from "./CliUiRoute"
import { FormattersRoute } from "./FormattersRoute"
import { KeybindsRoute } from "./KeybindsRoute"
import { McpRoute } from "./McpRoute"
import { ModelsAvailableRoute, ModelsDefaultRoute, ModelsRoute } from "./ModelsRoute"
import { OverviewRoute } from "./OverviewRoute"
import { PermissionsRoute } from "./PermissionsRoute"
import { ProvidersRoute } from "./ProvidersRoute"
import { ServersRoute } from "./ServersRoute"
import { SourcesRoute } from "./SourcesRoute"
import { ToolsRoute } from "./ToolsRoute"

export type ConfigSection = {
  path: string
  href: string
  icon: IconProps["name"]
  label: string
  component: Component
}

export type ConfigGroup = {
  id: string
  label: string
  items: ConfigSection[]
}

export type ConfigNode = ConfigSection | ConfigGroup

const providers = {
  path: "/providers",
  href: "/settings/providers",
  icon: "providers",
  label: "Providers",
  component: ProvidersRoute,
}
const agents = { path: "/agents", href: "/settings/agents", icon: "task", label: "Agents", component: AgentsRoute }
const tools = { path: "/tools", href: "/settings/tools", icon: "code", label: "Tools", component: ToolsRoute }
const mcp = { path: "/mcp", href: "/settings/mcp", icon: "mcp", label: "MCP", component: McpRoute }
const permissions = {
  path: "/permissions",
  href: "/settings/permissions",
  icon: "key",
  label: "Permissions",
  component: PermissionsRoute,
}

export const configNav: ConfigNode[] = [
  {
    id: "general",
    label: "General",
    items: [
      { path: "/", href: "/settings", icon: "home", label: "Overview", component: OverviewRoute },
    ],
  },
  providers,
  {
    id: "models",
    label: "Models",
    items: [
      {
        path: "/models/default",
        href: "/settings/models/default",
        icon: "models",
        label: "Defaults",
        component: ModelsDefaultRoute,
      },
      {
        path: "/models/explore",
        href: "/settings/models/explore",
        icon: "models",
        label: "Explore",
        component: ModelsAvailableRoute,
      },
    ],
  },
  agents,
  tools,
  mcp,
  permissions,
  {
    id: "cli",
    label: "CLI",
    items: [
      { path: "/keybinds", href: "/settings/keybinds", icon: "keyboard", label: "Keybinds", component: KeybindsRoute },
      { path: "/ui", href: "/settings/ui", icon: "sliders", label: "UI", component: CliUiRoute },
      {
        path: "/formatters",
        href: "/settings/formatters",
        icon: "sliders",
        label: "Formatters",
        component: FormattersRoute,
      },
    ],
  },
  {
    id: "advanced",
    label: "Advanced",
    items: [
      { path: "/servers", href: "/settings/servers", icon: "server", label: "Servers", component: ServersRoute },
      { path: "/sources", href: "/settings/sources", icon: "archive", label: "Sources", component: SourcesRoute },
    ],
  },
]

function sections(item: ConfigNode) {
  if ("items" in item) return item.items
  return [item]
}

export const configSections = [
  ...configNav.flatMap(sections),
  { path: "/agents/new", href: "/settings/agents/new", icon: "task", label: "New Agent", component: AgentBuilderRoute },
  { path: "/agents/:agentID", href: "/settings/agents", icon: "task", label: "Edit Agent", component: AgentBuilderRoute },
  { path: "/models", href: "/settings/models/default", icon: "models", label: "Models", component: ModelsRoute },
]
