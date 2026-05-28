import { For, Show } from "solid-js"
import { Button } from "@kilocode/kilo-web-ui/button"
import { ConfigRow, SectionTitle } from "@kilocode/kilo-web-ui/console"
import { IconButton } from "@kilocode/kilo-web-ui/icon-button"
import { CountTag, Tag } from "@kilocode/kilo-web-ui/tag"
import { toolName } from "../../shared/utils"
import { ConfigPage, SourceBadge } from "./ConfigPage"
import { actions, defs, usePermissionSettings, type PermissionAction, type PermissionRule } from "./state/permissions"

function tone(action: PermissionAction) {
  if (action === "allow") return "success"
  if (action === "deny") return "critical"
  return "warning"
}

function label(action: PermissionAction) {
  return actions.find((item) => item.value === action)?.label ?? action
}

function RuleMeta(props: { rule: PermissionRule }) {
  return (
    <div class="permission-row-meta">
      <Tag tone={tone(props.rule.action)}>{label(props.rule.action)}</Tag>
      <SourceBadge source={props.rule.source} inherited={props.rule.inherited} overridden={props.rule.overridden} />
    </div>
  )
}

export function PermissionsRoute() {
  const state = usePermissionSettings()

  return (
    <ConfigPage
      title={
        <span class="config-title-count">
          Permissions
          <CountTag>{state.rules().length}</CountTag>
        </span>
      }
      description="Control what tools agents can use by default and add pattern-specific allow, ask, or deny rules."
      actions={
        <Button icon="plus" variant="primary" disabled={Boolean(state.ctx.saving())} onClick={() => state.open()}>
          Add rule
        </Button>
      }
    >
      <div class="permissions">
        <For each={state.groups()}>
          {(group) => (
            <section class="permission-group">
              <SectionTitle
                trailing={<CountTag>{group.rules.length}</CountTag>}
                description={`${group.id} · ${group.description}`}
              >
                {group.title}
              </SectionTitle>

              <ConfigRow
                title="Default method"
                subtitle={`Used for ${group.noun}s that do not match a specific rule.`}
                status={
                  <div class="permission-row-meta">
                    <SourceBadge source={group.source} inherited={group.inherited} overridden={group.overridden} />
                  </div>
                }
                actions={
                  <select
                    class="permission-action-select"
                    aria-label={`Default method for ${group.title}`}
                    value={group.action}
                    disabled={Boolean(state.ctx.saving())}
                    onChange={(event) => state.setDefault(group.id, event.currentTarget.value as PermissionAction)}
                  >
                    <For each={actions}>{(item) => <option value={item.value}>{item.label}</option>}</For>
                  </select>
                }
              />

              <div class="permission-rules">
                <Show when={group.rules.length} fallback={<p class="permission-empty">No specific {group.noun} rules.</p>}>
                  <For each={group.rules}>
                    {(rule) => (
                      <ConfigRow
                        title={<span class="permission-pattern">{rule.pattern}</span>}
                        subtitle={`${group.title} ${group.noun} rule`}
                        status={<RuleMeta rule={rule} />}
                        actions={
                          <Show when={state.ctx.query()?.scope === "project" && rule.overridden}>
                            <IconButton
                              icon="trash"
                              variant="ghost"
                              aria-label={`Revert ${group.title} rule ${rule.pattern}`}
                              disabled={Boolean(state.ctx.saving())}
                              onClick={() => state.revert(rule)}
                            />
                          </Show>
                        }
                      />
                    )}
                  </For>
                </Show>
              </div>
            </section>
          )}
        </For>

        <Show when={state.other().length}>
          <section class="permission-group">
            <SectionTitle trailing={<CountTag>{state.other().length}</CountTag>} description="Additional tool permission rules from config.">
              Other Permissions
            </SectionTitle>
            <div class="permission-rules">
              <For each={state.other()}>
                {(rule) => (
                  <ConfigRow
                    title={toolName(rule.tool)}
                    subtitle={
                      <span class="permission-subtitle">
                        <span>{rule.tool}</span>
                        <span class="permission-pattern">{rule.pattern}</span>
                      </span>
                    }
                    status={<RuleMeta rule={rule} />}
                    actions={
                      <Show when={state.ctx.query()?.scope === "project" && rule.overridden}>
                        <IconButton
                          icon="trash"
                          variant="ghost"
                          aria-label={`Revert ${rule.tool} rule ${rule.pattern}`}
                          disabled={Boolean(state.ctx.saving())}
                          onClick={() => state.revert(rule)}
                        />
                      </Show>
                    }
                  />
                )}
              </For>
            </div>
          </section>
        </Show>
      </div>

      <Show when={state.mode() === "rule"}>
        <div class="drawer-scrim" onClick={state.close} />
        <aside class="provider-drawer permission-drawer" aria-label="Permission rule configuration">
          <header class="drawer-header">
            <div>
              <h2>Add Permission Rule</h2>
              <span>Create a pattern-specific rule for external directory, bash, read, or edit.</span>
            </div>
            <Button variant="ghost" aria-label="Close permission rule overlay" onClick={state.close}>
              X
            </Button>
          </header>

          <div class="provider-form permission-form">
            <label class="required-field wide">
              Permission type
              <select value={state.kind()} onChange={(event) => state.choose(event.currentTarget.value)}>
                <For each={defs}>{(def) => <option value={def.id}>{def.title}</option>}</For>
              </select>
            </label>
            <label class="required-field wide">
              {state.selected().noun === "command" ? "Command pattern" : "Path pattern"}
              <input
                value={state.pattern()}
                placeholder={state.selected().placeholder}
                spellcheck={false}
                onInput={(event) => state.setPattern(event.currentTarget.value)}
              />
            </label>
            <label class="required-field wide">
              Method
              <select
                value={state.action()}
                onChange={(event) => state.setAction(event.currentTarget.value as PermissionAction)}
              >
                <For each={actions}>{(item) => <option value={item.value}>{item.label}</option>}</For>
              </select>
            </label>
          </div>

          <footer class="drawer-footer">
            <Button variant="ghost" onClick={state.close}>
              Cancel
            </Button>
            <Button variant="primary" disabled={Boolean(state.ctx.saving())} onClick={state.add}>
              Save Rule
            </Button>
          </footer>
        </aside>
      </Show>
    </ConfigPage>
  )
}
