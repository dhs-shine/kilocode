import { For, Show } from "solid-js"
import { Button } from "@kilocode/kilo-ui/button"
import { Tag } from "@kilocode/kilo-ui/tag"
import { text } from "../../shared/utils"
import { ConfigPage, ConfigToolbar, SourceBadge } from "./ConfigPage"
import { useFormatterSettings } from "./state/formatters"

export function FormattersRoute() {
  const state = useFormatterSettings()

  return (
    <ConfigPage title="Formatters & LSP">
      <div class="advanced">
        <section>
          <h3>Formatters</h3>
          <ConfigToolbar description="Configure code formatters.">
            <label>
              <span>Formatter</span>
              <input
                value={state.fmt()}
                placeholder="Formatter"
                onInput={(event) => state.setFmt(event.currentTarget.value)}
              />
            </label>
            <label>
              <span>Command</span>
              <input
                value={state.fmtCommand()}
                placeholder="Command"
                onInput={(event) => state.setFmtCommand(event.currentTarget.value)}
              />
            </label>
            <label>
              <span>Extensions</span>
              <input
                value={state.fmtExtensions()}
                placeholder="Extensions"
                onInput={(event) => state.setFmtExtensions(event.currentTarget.value)}
              />
            </label>
            <Button variant="secondary" disabled={Boolean(state.ctx.saving())} onClick={state.addFormatter}>
              Save Formatter
            </Button>
          </ConfigToolbar>

          <div class="mini-list columns">
            <For each={state.formatters()}>
              {(item) => (
                <article class="mini-item simple" classList={{ inherited: item.inherited }}>
                  <strong>{item.key}</strong>
                  <span>{text(item.value)}</span>
                  <SourceBadge source={item.source} inherited={item.inherited} overridden={item.overridden} />
                </article>
              )}
            </For>
            <Show when={state.snap()}>
              {(data) => (
                <For each={data().formatter}>
                  {(item) => (
                    <article class="mini-item simple">
                      <strong>{item.name}</strong>
                      <Tag>{item.enabled ? "enabled" : "disabled"}</Tag>
                    </article>
                  )}
                </For>
              )}
            </Show>
          </div>
        </section>

        <section>
          <h3>Language Servers</h3>
          <ConfigToolbar description="Configure language server protocol integrations.">
            <label>
              <span>LSP</span>
              <input
                value={state.lsp()}
                placeholder="LSP"
                onInput={(event) => state.setLsp(event.currentTarget.value)}
              />
            </label>
            <label>
              <span>Command</span>
              <input
                value={state.lspCommand()}
                placeholder="Command"
                onInput={(event) => state.setLspCommand(event.currentTarget.value)}
              />
            </label>
            <label>
              <span>Extensions</span>
              <input
                value={state.lspExtensions()}
                placeholder="Extensions"
                onInput={(event) => state.setLspExtensions(event.currentTarget.value)}
              />
            </label>
            <Button variant="secondary" disabled={Boolean(state.ctx.saving())} onClick={state.addLsp}>
              Save LSP
            </Button>
          </ConfigToolbar>

          <div class="mini-list columns">
            <For each={state.lsps()}>
              {(item) => (
                <article class="mini-item simple" classList={{ inherited: item.inherited }}>
                  <strong>{item.key}</strong>
                  <span>{text(item.value)}</span>
                  <SourceBadge source={item.source} inherited={item.inherited} overridden={item.overridden} />
                </article>
              )}
            </For>
            <Show when={state.snap()}>
              {(data) => (
                <For each={data().lsp}>
                  {(item) => (
                    <article class="mini-item simple">
                      <strong>{item.name}</strong>
                      <span>{item.status}</span>
                    </article>
                  )}
                </For>
              )}
            </Show>
          </div>
        </section>
      </div>
    </ConfigPage>
  )
}
