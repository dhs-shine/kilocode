import { For, Show } from "solid-js"
import { Card } from "@kilocode/kilo-web-ui/card"
import { Tag } from "@kilocode/kilo-web-ui/tag"
import { useConfig } from "../../context/config"
import { size, text } from "../../shared/utils"
import { ConfigPage, ConfigToolbar } from "./ConfigPage"

export function OverviewRoute() {
  const ctx = useConfig()

  return (
    <Show when={ctx.data()}>
      {(snap) => (
        <Show when={ctx.query()}>
          {(q) => {
            const cfg = () => snap().effective
            const rows = () => [
              { label: "Default model", value: text(cfg().model) },
              { label: "Small model", value: text(cfg().small_model) },
              { label: "Default agent", value: text(cfg().default_agent) },
              { label: "Providers", value: String(snap().providers.all.length) },
              { label: "Agents", value: String(size(cfg().agent)) },
              { label: "MCP servers", value: String(size(cfg().mcp)) },
            ]

            return (
              <ConfigPage title="Overview" actions={<Tag>{snap().health.healthy ? "Healthy" : "Unavailable"}</Tag>}>
                <ConfigToolbar
                  title="Server"
                  description={`Version ${snap().health.version}`}
                  meta={<Tag>{q().scope}</Tag>}
                />

                <section class="grid metrics">
                  <For each={rows()}>
                    {(row) => (
                      <Card class="metric">
                        <span>{row.label}</span>
                        <strong>{row.value}</strong>
                      </Card>
                    )}
                  </For>
                </section>

                <section class="grid details">
                  <Card class="panel">
                    <div class="title">
                      <div>
                        <h2>Diagnostics</h2>
                        <p>Current API target and overlay state.</p>
                      </div>
                      <Tag>{q().scope}</Tag>
                    </div>
                    <dl class="facts">
                      <div>
                        <dt>Server URL</dt>
                        <dd>{q().url}</dd>
                      </div>
                      <div>
                        <dt>Directory</dt>
                        <dd>{q().dir || "Server default"}</dd>
                      </div>
                    </dl>
                  </Card>
                </section>
              </ConfigPage>
            )
          }}
        </Show>
      )}
    </Show>
  )
}
