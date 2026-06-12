/** @jsxImportSource solid-js */
import { For } from "solid-js"
import type { Meta, StoryObj } from "storybook-solidjs-vite"
import { Part } from "@kilocode/kilo-ui/message-part"
import type { AssistantMessage as SDKAssistantMessage, Part as SDKPart, ToolPart } from "@kilocode/sdk/v2"
import { StoryProviders } from "./StoryProviders"
import { registerVscodeToolOverrides } from "../components/chat/VscodeToolOverrides"

registerVscodeToolOverrides()

const SID = "tool-call-lab-session"
const MID = "tool-call-lab-message"
const stamp = Date.now()

const base: SDKAssistantMessage = {
  id: MID,
  sessionID: SID,
  role: "assistant",
  parentID: "tool-call-lab-user-message",
  time: { created: stamp - 9000, completed: stamp - 1000 },
  modelID: "anthropic/claude-sonnet-4-6",
  providerID: "kilo",
  mode: "default",
  agent: "default",
  path: { cwd: "/project", root: "/project" },
  cost: 0.0021,
  tokens: { total: 742, input: 386, output: 356, reasoning: 0, cache: { read: 0, write: 0 } },
}

const long = [
  "packages/kilo-vscode/webview-ui/src/components/chat/VscodeToolOverrides.tsx",
  "packages/kilo-vscode/webview-ui/src/stories/tool-call-lab.stories.tsx",
  "packages/kilo-ui/src/components/message-part.tsx",
].join("\n")

const hits = [
  "packages/kilo-ui/src/components/message-part.tsx:1847: <div data-component=\"tool-output\">",
  "packages/kilo-ui/src/components/basic-tool.css:250: [data-component=\"tool-output\"]",
  "packages/kilo-vscode/webview-ui/src/components/chat/VscodeToolOverrides.tsx:141: background process output",
].join("\n")

const tree = [
  "packages/kilo-ui/src/components/",
  "  basic-tool.css",
  "  context-tool-results.tsx",
  "  message-part.css",
  "  message-part.tsx",
  "packages/kilo-vscode/webview-ui/src/stories/",
  "  tool-call-lab.stories.tsx",
].join("\n")

const proc = [
  "pid: 48122",
  "status: running",
  "cwd: /project",
  "command: bun run --cwd packages/kilo-vscode storybook",
  "last_output:",
  "Storybook 9.0.18 for solid-vite started",
  "Local: http://localhost:6007/",
].join("\n")

function completed(input: Record<string, unknown>, title: string, value: string): ToolPart["state"] {
  return {
    status: "completed",
    input,
    output: value,
    title,
    metadata: {},
    time: { start: stamp - 5000, end: stamp - 4400 },
  }
}

function tool(id: string, call: string, name: string, state: ToolPart["state"]): ToolPart {
  return {
    id,
    sessionID: SID,
    messageID: MID,
    type: "tool",
    callID: call,
    tool: name,
    state,
  }
}

const previews: SDKPart[] = [
  tool(
    "lab-preview-glob",
    "lab-call-preview-glob",
    "glob",
    completed({ pattern: "packages/**/tool*.tsx", path: "." }, "Find tool files", long),
  ),
  tool(
    "lab-preview-grep",
    "lab-call-preview-grep",
    "grep",
    completed({ pattern: "data-component=\"tool-output\"", include: "*.tsx", path: "." }, "Search tool output", hits),
  ),
  tool(
    "lab-preview-list",
    "lab-call-preview-list",
    "list",
    completed({ path: "packages/kilo-ui/src/components" }, "List component files", tree),
  ),
  tool(
    "lab-preview-background",
    "lab-call-preview-background",
    "background_process",
    completed({ action: "status", id: "bgp_storybook", description: "Check Storybook server" }, "Check Storybook server", proc),
  ),
]

const meta: Meta = {
  title: "Labs/Tool Call Lab",
  parameters: { layout: "padded" },
}

export default meta

type Story = StoryObj

export const SearchPreviews: Story = {
  name: "Search Previews",
  render: () => (
    <StoryProviders noPadding sessionID={SID} status="idle">
      <div style={{ padding: "16px", width: "520px", "max-width": "100%" }}>
        <div style={{ display: "flex", "flex-direction": "column", gap: "10px" }}>
          <For each={previews}>{(part) => <Part part={part} message={base} defaultOpen />}</For>
        </div>
      </div>
    </StoryProviders>
  ),
}
