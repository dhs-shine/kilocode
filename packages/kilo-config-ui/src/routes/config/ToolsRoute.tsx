import { For, Show } from "solid-js"
import { Tag } from "@kilocode/kilo-ui/tag"
import { useConfig } from "../../context/config"
import { ConfigPage, ConfigToolbar } from "./ConfigPage"

export function ToolsRoute() {
  const ctx = useConfig()
  const snap = () => ctx.data()

  return (
    <Show when={snap()}>
      {(data) => (
        <ConfigPage title="Tool Inventory" actions={<Tag>{data().tools.length}</Tag>}>
          <ConfigToolbar
            title="Registered Tools"
            description="All registered tools and MCP server connection status."
          />

          <div class="tag-cloud">
            <For each={data().tools}>{(tool) => <Tag>{tool}</Tag>}</For>
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
