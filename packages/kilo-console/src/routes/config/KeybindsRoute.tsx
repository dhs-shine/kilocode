import { For, Show } from "solid-js"
import { Button } from "@kilocode/kilo-web-ui/button"
import { ConfigPage, ConfigToolbar } from "./ConfigPage"
import { useKeybindSettings } from "./state/keybinds"

export function KeybindsRoute() {
  const state = useKeybindSettings()

  return (
    <ConfigPage title="TUI Keybinds">
      <ConfigToolbar title="Keybind Override" description="Override default terminal UI keyboard shortcuts.">
        <label>
          <span>Key</span>
          <input
            list="keybind-list"
            value={state.key()}
            placeholder="Key"
            onInput={(event) => state.setKey(event.currentTarget.value)}
          />
        </label>
        <label>
          <span>Binding</span>
          <input
            value={state.binding()}
            placeholder="Binding"
            onInput={(event) => state.setBinding(event.currentTarget.value)}
          />
        </label>
        <Button variant="secondary" disabled={Boolean(state.ctx.saving()) || !state.key()} onClick={state.save}>
          Save Keybind
        </Button>
      </ConfigToolbar>

      <datalist id="keybind-list">
        <For each={state.keybinds()}>{([name]) => <option value={name} />}</For>
      </datalist>

      <Show when={state.conflicts().length}>
        <p class="warning">Duplicate binding with: {state.conflicts().join(", ")}</p>
      </Show>

      <div class="mini-list columns">
        <For each={state.keybinds()}>
          {([name, binding]) => (
            <article class="mini-item simple">
              <strong>{name}</strong>
              <span>{binding}</span>
            </article>
          )}
        </For>
      </div>
    </ConfigPage>
  )
}
