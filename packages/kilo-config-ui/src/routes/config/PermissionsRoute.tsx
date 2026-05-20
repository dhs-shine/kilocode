import { For, Show } from "solid-js"
import { Button } from "@kilocode/kilo-ui/button"
import { Tag } from "@kilocode/kilo-ui/tag"
import { toAction } from "../../shared/utils"
import { ConfigPage, ConfigToolbar, SourceBadge } from "./ConfigPage"
import { usePermissionSettings } from "./state/permissions"

export function PermissionsRoute() {
  const state = usePermissionSettings()

  return (
    <ConfigPage title="Permission Rules" actions={<Tag>{state.rules().length}</Tag>}>
      <ConfigToolbar title="Add Rule" description="Scalar and pattern-specific tool permission overrides.">
        <label>
          <span>Tool</span>
          <input
            value={state.tool()}
            placeholder="Tool"
            onInput={(event) => state.setTool(event.currentTarget.value)}
          />
        </label>
        <label>
          <span>Pattern</span>
          <input
            value={state.pattern()}
            placeholder="Pattern"
            onInput={(event) => state.setPattern(event.currentTarget.value)}
          />
        </label>
        <label>
          <span>Action</span>
          <select value={state.action()} onChange={(event) => state.setAction(toAction(event.currentTarget.value))}>
            <option value="ask">Ask</option>
            <option value="allow">Allow</option>
            <option value="deny">Deny</option>
          </select>
        </label>
        <Button variant="secondary" disabled={Boolean(state.ctx.saving())} onClick={state.add}>
          Add Rule
        </Button>
      </ConfigToolbar>

      <div class="mini-list">
        <Show when={state.rules().length} fallback={<p class="empty">No permission rules configured.</p>}>
          <For each={state.rules()}>
            {(rule) => (
              <article class="mini-item simple" classList={{ inherited: rule.inherited }}>
                <strong>{rule.tool}</strong>
                <span>{rule.pattern}</span>
                <div class="tags">
                  <Tag>{rule.action}</Tag>
                  <SourceBadge source={rule.source} inherited={rule.inherited} overridden={rule.overridden} />
                </div>
                <Show when={state.ctx.query()?.scope === "project" && rule.overridden}>
                  <Button variant="secondary" disabled={Boolean(state.ctx.saving())} onClick={() => state.revert(rule)}>
                    Revert
                  </Button>
                </Show>
              </article>
            )}
          </For>
        </Show>
      </div>
    </ConfigPage>
  )
}
