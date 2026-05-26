import { createMemo, For, Show } from "solid-js"
import { Tag } from "@kilocode/kilo-ui/tag"
import { useConfig } from "../../context/config"
import { toolCapabilities, toolName } from "../../shared/utils"
import { ConfigPage, ConfigToolbar } from "./ConfigPage"

export function ToolsRoute() {
  const ctx = useConfig()
  const snap = () => ctx.data()
  const rows = createMemo(() => {
    const data = snap()
    if (!data) return []
    const details = new Map(data.toolDetails.map((item) => [item.id, item]))
    return data.tools
      .map((id) => ({ id, detail: details.get(id) }))
      .sort((a, b) => toolName(a.id).localeCompare(toolName(b.id)))
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
                          <strong>{toolName(tool.id)}</strong>
                          <span>{tool.id}</span>
                        </div>
                      </div>
                    </div>
                    <ul class="tool-capabilities">
                      <For each={toolCapabilities(tool)}>{(cap) => <li>{cap}</li>}</For>
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
