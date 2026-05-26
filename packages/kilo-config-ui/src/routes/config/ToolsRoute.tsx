import { createMemo, For, Show } from "solid-js"
import { Tag } from "@kilocode/kilo-ui/tag"
import type { ToolListItem } from "@kilocode/sdk/v2/client"
import { useConfig } from "../../context/config"
import { ConfigPage, ConfigToolbar } from "./ConfigPage"

const labels: Record<string, string> = {
  apply_patch: "Apply Patch",
  bash: "Bash",
  edit: "Edit File",
  glob: "Find Files",
  grep: "Search Content",
  lsp: "Language Server",
  question: "Ask User",
  read: "Read File",
  recall: "Local Recall",
  skill: "Load Skill",
  task: "Subagent Task",
  todowrite: "Todo List",
  webfetch: "Fetch URL",
  websearch: "Web Search",
  write: "Write File",
}

const copy: Record<string, string[]> = {
  apply_patch: ["Apply structured patches to add, update, move, or delete files.", "Keeps edits explicit and easy to review."],
  bash: ["Run shell commands in the current workspace.", "Supports timeouts, working directories, and command descriptions."],
  edit: ["Replace exact text inside an existing file.", "Can update one match or all matches when needed."],
  glob: ["Find files by path patterns.", "Useful for locating files before reading or editing them."],
  grep: ["Search file contents with regular expressions.", "Can limit results to a file pattern such as TypeScript or CSS files."],
  lsp: ["Use language-server features for code navigation.", "Supports operations such as definitions, references, and diagnostics."],
  question: ["Ask the user for a decision during execution.", "Supports single-choice and multi-choice prompts."],
  read: ["Read files, directories, images, and PDFs from the workspace.", "Supports line offsets and limits for large files."],
  recall: ["Search and read previous project conversations.", "Useful for recovering prior decisions or implementation context."],
  skill: ["Load task-specific instructions and workflows.", "Applies specialized repo guidance when a matching skill exists."],
  task: ["Delegate focused exploration or implementation work to a subagent.", "Useful for parallel codebase research and complex subtasks."],
  todowrite: ["Track multi-step implementation work.", "Shows progress with pending, in-progress, and completed states."],
  webfetch: ["Fetch and convert web pages for analysis.", "Supports markdown, text, and HTML output formats."],
  websearch: ["Search the web for external information.", "Supports fast, automatic, and deep search modes."],
  write: ["Create or overwrite files with complete contents.", "Best for new files or full-file replacements."],
}

const acronyms = new Set(["api", "html", "id", "json", "lsp", "mcp", "pdf", "url"])

type Item = { id: string; detail: ToolListItem | undefined }

function word(input: string) {
  const lower = input.toLowerCase()
  if (acronyms.has(lower)) return lower.toUpperCase()
  return input.charAt(0).toUpperCase() + input.slice(1)
}

function name(id: string) {
  const known = labels[id]
  if (known) return known
  const clean = id
    .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
    .replace(/[._:/-]+/g, " ")
    .replace(/\s+/g, " ")
    .trim()
  if (!clean) return id
  return clean.split(" ").map(word).join(" ")
}

function clip(input: string) {
  if (input.length <= 180) return input
  return `${input.slice(0, 177)}...`
}

function summary(input: string) {
  const text = input
    .replace(/```[\s\S]*?```/g, " ")
    .replace(/[*`#>_-]+/g, " ")
    .replace(/\s+/g, " ")
    .trim()
  const parts = text
    .split(/(?:\.|\?|!)\s+/)
    .map((part) => part.trim())
    .filter(Boolean)
    .slice(0, 2)
  if (parts.length) return parts.map(clip)
  return text ? [clip(text)] : []
}

function capabilities(item: Item) {
  const known = copy[item.id]
  if (known) return known
  const desc = item.detail?.description.trim()
  if (desc) return summary(desc)
  return ["No capability description is available for this tool."]
}

export function ToolsRoute() {
  const ctx = useConfig()
  const snap = () => ctx.data()
  const rows = createMemo(() => {
    const data = snap()
    if (!data) return []
    const details = new Map(data.toolDetails.map((item) => [item.id, item]))
    return data.tools
      .map((id) => ({ id, detail: details.get(id) }))
      .sort((a, b) => name(a.id).localeCompare(name(b.id)))
  })

  return (
    <Show when={snap()}>
      {(data) => (
        <ConfigPage title="Tool Inventory" actions={<Tag>{data().tools.length}</Tag>}>
          <ConfigToolbar
            title="Registered Tools"
            description="All registered tools and MCP server connection status."
          />

          <div class="tools">
            <Show when={rows().length} fallback={<p class="empty">No tools registered.</p>}>
              <For each={rows()}>
                {(tool) => (
                  <article class="model tool-card">
                    <div class="model-main tool-main">
                      <div class="model-title">
                        <div>
                          <strong>{name(tool.id)}</strong>
                          <span>{tool.id}</span>
                        </div>
                      </div>
                    </div>
                    <ul class="tool-capabilities">
                      <For each={capabilities(tool)}>{(cap) => <li>{cap}</li>}</For>
                    </ul>
                  </article>
                )}
              </For>
            </Show>
          </div>

          <Show when={Object.keys(data().mcp).length}>
            <div class="mini-list spaced">
              <For each={Object.entries(data().mcp)}>
                {([name, status]) => (
                  <article class="mini-item simple">
                    <strong>{name}</strong>
                    <span>{status.status}</span>
                  </article>
                )}
              </For>
            </div>
          </Show>
        </ConfigPage>
      )}
    </Show>
  )
}
