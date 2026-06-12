/** @jsxImportSource solid-js */
import type { Meta, StoryObj } from "storybook-solidjs-vite"
import { Icon } from "../components/icon"

const meta: Meta<typeof Icon> = {
  title: "Components/Icon",
  component: Icon,
  argTypes: {
    size: { control: "select", options: ["small", "normal", "medium", "large"] },
  },
}

export default meta
type Story = StoryObj<typeof Icon>

export const Default: Story = {
  args: { name: "settings-gear", size: "normal" },
}

export const Small: Story = {
  args: { name: "settings-gear", size: "small" },
}

export const Medium: Story = {
  args: { name: "settings-gear", size: "medium" },
}

export const Large: Story = {
  args: { name: "settings-gear", size: "large" },
}

const iconNames = [
  "align-right",
  "arrow-up",
  "arrow-left",
  "arrow-right",
  "archive",
  "brain",
  "bullet-list",
  "check-small",
  "chevron-down",
  "chevron-right",
  "circle-x",
  "close",
  "code",
  "code-lines",
  "collapse",
  "console",
  "copy",
  "edit",
  "eye",
  "folder",
  "github",
  "magnifying-glass",
  "plus-small",
  "plus",
  "pencil-line",
  "settings-gear",
  "trash",
  "sliders",
  "check",
  "share",
  "download",
  "menu",
  "expand",
  "bubble-5",
  "checklist",
  "circle-check",
  "circle-ban-sign",
  "discord",
  "dot-grid",
  "comment",
  "branch",
  "help",
  "link",
  "providers",
  "models",
  "mcp",
  "photo",
  "enter",
  "server",
  "keyboard",
  "selector",
  "arrow-down-to-line",
  "task",
  "stop",
  "layout-left",
  "layout-right",
  "layout-bottom",
] as const

const toolIcons = [
  { tool: "Read", icon: "glasses", note: "File reads" },
  { tool: "List", icon: "bullet-list", note: "Directory lists" },
  { tool: "Glob", icon: "magnifying-glass-menu", note: "File pattern search" },
  { tool: "Grep", icon: "magnifying-glass-menu", note: "Content search" },
  { tool: "WebFetch", icon: "window-cursor", note: "Fetch URL" },
  { tool: "WebSearch", icon: "window-cursor", note: "Search web" },
  { tool: "CodeSearch", icon: "code", note: "Code search" },
  { tool: "Task", icon: "task", note: "Subagent" },
  { tool: "Bash", icon: "console", note: "Shell command" },
  { tool: "Edit", icon: "code-lines", note: "Modify file" },
  { tool: "Write", icon: "code-lines", note: "Write file" },
  { tool: "Apply Patch", icon: "code-lines", note: "Patch files" },
  { tool: "TodoWrite", icon: "checklist", note: "Update todos" },
  { tool: "TodoRead", icon: "checklist", note: "Read todos" },
  { tool: "Question", icon: "bubble-5", note: "Ask user" },
  { tool: "Skill", icon: "brain", note: "Load skill" },
  { tool: "MCP/default", icon: "mcp", note: "Fallback tool" },
] as const

export const AllIcons: Story = {
  render: () => (
    <div
      style={{
        display: "grid",
        "grid-template-columns": "repeat(auto-fill, 80px)",
        gap: "12px",
        padding: "16px",
      }}
    >
      {iconNames.map((name) => (
        <div
          style={{
            display: "flex",
            "flex-direction": "column",
            "align-items": "center",
            gap: "4px",
          }}
        >
          <Icon name={name} size="normal" />
          <span style={{ "font-size": "10px", color: "var(--text-weak)", "text-align": "center" }}>{name}</span>
        </div>
      ))}
    </div>
  ),
  parameters: { layout: "fullscreen" },
}

export const ToolCallIcons: Story = {
  render: () => (
    <div
      style={{
        display: "grid",
        "grid-template-columns": "repeat(auto-fill, minmax(180px, 1fr))",
        gap: "8px",
        padding: "16px",
      }}
    >
      {toolIcons.map((item) => (
        <div
          style={{
            display: "grid",
            "grid-template-columns": "24px 1fr",
            gap: "8px",
            "align-items": "center",
            padding: "8px",
            "border-radius": "6px",
            background: "var(--surface-inset-base)",
          }}
        >
          <Icon name={item.icon} size="normal" />
          <div style={{ display: "flex", "flex-direction": "column", gap: "2px", "min-width": "0" }}>
            <span style={{ "font-size": "12px", color: "var(--text-strong)" }}>{item.tool}</span>
            <span style={{ "font-size": "11px", color: "var(--text-weak)" }}>{item.icon}</span>
            <span style={{ "font-size": "11px", color: "var(--text-weaker)" }}>{item.note}</span>
          </div>
        </div>
      ))}
    </div>
  ),
  parameters: { layout: "fullscreen" },
}
