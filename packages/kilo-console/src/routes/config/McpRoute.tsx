import { For, Show } from "solid-js"
import { Button } from "@kilocode/kilo-web-ui/button"
import { Tag } from "@kilocode/kilo-web-ui/tag"
import { text } from "../../shared/utils"
import { ConfigPage, ConfigToolbar, SourceBadge } from "./ConfigPage"
import { useMcpSettings } from "./state/mcp"

export function McpRoute() {
  const state = useMcpSettings()

  return (
    <ConfigPage title="MCP Servers" actions={<Tag>{state.mcp().length}</Tag>}>
      <ConfigToolbar title="Add MCP Server" description="Register project or global Model Context Protocol servers.">
        <label>
          <span>Name</span>
          <input
            value={state.name()}
            placeholder="Name"
            onInput={(event) => state.setName(event.currentTarget.value)}
          />
        </label>
        <label>
          <span>Command or URL</span>
          <input
            value={state.value()}
            placeholder="node server.js or https://mcp.example.com"
            onInput={(event) => state.setValue(event.currentTarget.value)}
          />
        </label>
        <Button variant="secondary" disabled={Boolean(state.ctx.saving())} onClick={state.add}>
          Add MCP
        </Button>
      </ConfigToolbar>

      <div class="mini-list">
        <Show when={state.snap()}>
          {(data) => (
            <For each={state.mcp()}>
              {(item) => (
                <article class="mini-item" classList={{ inherited: item.inherited }}>
                  <div>
                    <strong>{item.key}</strong>
                    <span>{text(item.value)}</span>
                  </div>
                  <div class="tags">
                    <SourceBadge source={item.source} inherited={item.inherited} overridden={item.overridden} />
                    <Tag>{data().mcp[item.key]?.status ?? "configured"}</Tag>
                  </div>
                  <Button variant="secondary" disabled={Boolean(state.ctx.saving())} onClick={() => state.toggle(item)}>
                    Toggle
                  </Button>
                  <Show when={state.ctx.query()?.scope === "project" && item.overridden}>
                    <Button
                      variant="secondary"
                      disabled={Boolean(state.ctx.saving())}
                      onClick={() => state.revert(item)}
                    >
                      Revert
                    </Button>
                  </Show>
                </article>
              )}
            </For>
          )}
        </Show>
      </div>
    </ConfigPage>
  )
}
