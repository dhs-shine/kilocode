import { createMemo, For, Show, type JSX } from "solid-js"
import { useLocation, useNavigate, useParams } from "@solidjs/router"
import { Button } from "@kilocode/kilo-web-ui/button"
import { IconButton } from "@kilocode/kilo-web-ui/icon-button"
import { Tag } from "@kilocode/kilo-web-ui/tag"
import { SearchField } from "../../components/SearchField"
import { useConfig } from "../../context/config"
import { toAction, toMode, toolCapabilities, toolName } from "../../shared/utils"
import { ConfigPage, SourceBadge } from "./ConfigPage"
import {
  agentEditable,
  agentTitle,
  snippets,
  useAgentBuilder,
  type AgentEntry,
  type AgentItem,
} from "./state/agents"

type Row = { item: AgentItem; entry?: AgentEntry; rank: number }

function base(input: string) {
  const index = input.indexOf("/settings")
  if (index > 0) return `${input.slice(0, index)}/settings`
  if (input.startsWith("/config")) return "/config"
  return "/settings"
}

function desc(item: AgentItem) {
  return item.description ?? "No description available."
}

function label(rank: number) {
  if (rank === 0) return "Project Agents"
  return "Global Agents"
}

function useAgentLinks() {
  const loc = useLocation()
  const nav = useNavigate()
  const href = (id?: string) => {
    const suffix = id ? `/${encodeURIComponent(id)}` : ""
    return `${base(loc.pathname)}/agents${suffix}${loc.search}`
  }
  return { href, nav }
}

function countTools(input: string[]) {
  if (input.length === 1) return "1 tool"
  return `${input.length} tools`
}

function FieldCard(props: { label: string; actions?: JSX.Element; children: JSX.Element }) {
  return (
    <article class="resolved-card default-model-card agent-field-card">
      <header class="default-model-header">
        <span>{props.label}</span>
        <Show when={props.actions}>
          <div class="tags default-model-actions">{props.actions}</div>
        </Show>
      </header>
      <div class="default-model-value">{props.children}</div>
    </article>
  )
}

export function AgentsRoute() {
  const links = useAgentLinks()
  const ctx = useConfig()
  const snap = () => ctx.data()
  const rows = createMemo(() => {
    const data = snap()
    if (!data) return []
    const scope = ctx.query()?.scope ?? "global"
    const entries = new Map((data.overlay.collections.agent ?? []).map((entry) => [entry.key, entry]))
    const local = new Set(
      (data.overlay.collections.agent ?? []).filter((entry) => entry.source === "project").map((entry) => entry.key),
    )
    return data.agents
      .filter((item) => {
        const entry = entries.get(item.name)
        if (scope === "global") return !entry?.local
        return local.has(item.name) || !entry?.local
      })
      .map((item) => ({ item, entry: entries.get(item.name), rank: local.has(item.name) ? 0 : 1 }))
      .sort((a, b) => a.rank - b.rank || agentTitle(a.item).localeCompare(agentTitle(b.item)))
  })
  const groups = createMemo(() => [0, 1].map((rank) => rows().filter((row) => row.rank === rank)).filter((row) => row.length))

  return (
    <Show when={snap()}>
      {(data) => (
        <ConfigPage
          title="Agents"
          actions={
            <>
              <Button variant="primary" onClick={() => links.nav(links.href("new"))}>
                New Agent
              </Button>
              <Tag>{rows().length}</Tag>
            </>
          }
        >
          <div class="agents">
            <Show when={groups().length} fallback={<p class="empty">No agents loaded.</p>}>
              <For each={groups()}>
                {(group) => (
                  <section class="agent-section">
                    <Show when={ctx.query()?.scope === "project"}>
                      <h2>{label(group[0]?.rank ?? 1)}</h2>
                    </Show>
                    <For each={group}>
                      {(row) => (
                        <article class="model agent-card" classList={{ inherited: row.entry?.inherited }}>
                          <div class="model-main">
                            <div class="model-title">
                              <div>
                                <strong>{agentTitle(row.item)}</strong>
                                <span>{row.item.name}</span>
                              </div>
                            </div>
                            <Button variant="secondary" onClick={() => links.nav(links.href(row.item.name))}>
                              {agentEditable(row.item, row.entry) ? "Edit" : "Inspect"}
                            </Button>
                          </div>
                          <p class="model-description">{desc(row.item)}</p>
                          <div class="tags agent-tags">
                            <Tag>{row.item.mode}</Tag>
                            <Tag>{row.item.native ? "Native" : "Custom"}</Tag>
                            <Show when={row.item.hidden}>
                              <Tag>Hidden</Tag>
                            </Show>
                            <Show when={row.item.deprecated}>
                              <Tag>Deprecated</Tag>
                            </Show>
                            <Show when={row.entry}>
                              {(entry) => (
                                <SourceBadge
                                  source={entry().source}
                                  inherited={entry().inherited}
                                  overridden={entry().overridden}
                                />
                              )}
                            </Show>
                          </div>
                        </article>
                      )}
                    </For>
                  </section>
                )}
              </For>
            </Show>
          </div>
        </ConfigPage>
      )}
    </Show>
  )
}

export function AgentBuilderRoute() {
  const links = useAgentLinks()
  const params = useParams()
  const agent = () => (params.agentID === "new" ? undefined : params.agentID)
  const state = useAgentBuilder(agent)
  const title = createMemo(() => {
    if (!agent()) return "Agent Builder"
    if (state.locked()) return "Inspect Agent"
    return "Edit Agent"
  })

  return (
    <Show when={state.snap()}>
      {(_data) => (
        <ConfigPage
          title={title()}
          actions={
            <>
              <Button variant="secondary" disabled={Boolean(state.ctx.saving()) || !state.ready()} onClick={state.openMarkdown}>
                Markdown
              </Button>
              <Show when={!state.locked()}>
                <Button variant="primary" disabled={Boolean(state.ctx.saving()) || !state.ready()} onClick={state.save}>
                  Save
                </Button>
              </Show>
              <IconButton
                icon="close"
                variant="secondary"
                aria-label="Close agent builder"
                onClick={() => links.nav(links.href())}
              />
            </>
          }
        >
          <div class="builder">
            <section class="builder-form">
              <div class="resolved-grid model-defaults agent-fields">
                <FieldCard label="Agent id">
                  <input
                    value={state.id()}
                    placeholder="reviewer"
                    readOnly={state.locked()}
                    spellcheck={false}
                    onInput={(event) => state.setId(event.currentTarget.value)}
                  />
                </FieldCard>
                <FieldCard label="Description">
                  <input
                    value={state.desc()}
                    placeholder="Review code and report risks"
                    readOnly={state.locked()}
                    onInput={(event) => state.setDesc(event.currentTarget.value)}
                  />
                </FieldCard>
                <FieldCard label="Mode">
                  <select
                    value={state.mode()}
                    disabled={state.locked()}
                    onChange={(event) => state.setMode(toMode(event.currentTarget.value))}
                  >
                    <option value="primary">Primary</option>
                    <option value="subagent">Subagent</option>
                    <option value="all">Both</option>
                  </select>
                </FieldCard>
                <FieldCard
                  label="Model"
                  actions={
                    <>
                      <Show when={!state.locked() && state.model()}>
                        <Button variant="secondary" disabled={Boolean(state.ctx.saving())} onClick={state.clearModel}>
                          Use Default
                        </Button>
                      </Show>
                      <IconButton
                        icon="edit"
                        variant="secondary"
                        aria-label="Edit agent model"
                        disabled={Boolean(state.ctx.saving()) || state.locked()}
                        onClick={state.openModel}
                      />
                    </>
                  }
                >
                  <Show
                    when={state.selected()}
                    fallback={
                      <>
                        <strong>{state.model() || "Inherit default model"}</strong>
                        <Show when={state.model()}>{(value) => <span class="default-model-id">{value()}</span>}</Show>
                      </>
                    }
                  >
                    {(model) => (
                      <>
                        <strong>{`${model().provider.name} / ${model().model.name}`}</strong>
                        <span class="default-model-id">{model().id}</span>
                      </>
                    )}
                  </Show>
                </FieldCard>
                <FieldCard label="Color">
                  <input
                    value={state.color()}
                    placeholder="blue"
                    readOnly={state.locked()}
                    onInput={(event) => state.setColor(event.currentTarget.value)}
                  />
                </FieldCard>
                <FieldCard label="Max steps">
                  <input
                    value={state.steps()}
                    placeholder="optional"
                    inputMode="numeric"
                    readOnly={state.locked()}
                    onInput={(event) => state.setSteps(event.currentTarget.value)}
                  />
                </FieldCard>
                <FieldCard
                  label="Tool Access"
                  actions={
                    <>
                      <Show when={!state.locked() && state.tools().length}>
                        <Button variant="secondary" disabled={Boolean(state.ctx.saving())} onClick={state.clearTools}>
                          Clear
                        </Button>
                      </Show>
                      <IconButton
                        icon="edit"
                        variant="secondary"
                        aria-label="Edit tool access"
                        disabled={Boolean(state.ctx.saving()) || state.locked()}
                        onClick={state.openTools}
                      />
                    </>
                  }
                >
                  <Show
                    when={state.tools().length}
                    fallback={
                      <>
                        <strong>No tools selected</strong>
                        <span class="default-model-id">No allow permissions will be written.</span>
                      </>
                    }
                  >
                    <strong>{countTools(state.tools())}</strong>
                    <div class="tag-cloud agent-tool-summary">
                      <For each={state.tools()}>{(tool) => <Tag>{tool}</Tag>}</For>
                    </div>
                  </Show>
                </FieldCard>
                <FieldCard label="Prompt">
                  <textarea
                    value={state.prompt()}
                    placeholder="Describe how this agent should behave."
                    readOnly={state.locked()}
                    onInput={(event) => state.setPrompt(event.currentTarget.value)}
                  />
                </FieldCard>
              </div>

              <div class="builder-block">
                <div class="block-title">
                  <strong>Prompt Snippets</strong>
                  <span>Insert a starter instruction into the prompt, then customize it.</span>
                </div>
                <div class="snippet-list">
                  <For each={snippets}>
                    {(snippet) => (
                      <Button
                        variant="secondary"
                        disabled={Boolean(state.ctx.saving()) || state.locked()}
                        onClick={() => state.insert(snippet)}
                      >
                        {snippet}
                      </Button>
                    )}
                  </For>
                </div>
              </div>

              <div class="builder-block">
                <div class="block-title">
                  <strong>Agent Permissions</strong>
                  <span>Add scalar or pattern-specific overrides before previewing markdown.</span>
                </div>
                <div class="model-controls compact">
                  <label>
                    Tool
                    <input
                      value={state.permTool()}
                      placeholder="bash"
                      readOnly={state.locked()}
                      onInput={(event) => state.setPermTool(event.currentTarget.value)}
                    />
                  </label>
                  <label>
                    Pattern
                    <input
                      value={state.permPattern()}
                      placeholder="optional pattern"
                      readOnly={state.locked()}
                      onInput={(event) => state.setPermPattern(event.currentTarget.value)}
                    />
                  </label>
                  <label>
                    Action
                    <select
                      value={state.permAction()}
                      disabled={state.locked()}
                      onChange={(event) => state.setPermAction(toAction(event.currentTarget.value))}
                    >
                      <option value="ask">Ask</option>
                      <option value="allow">Allow</option>
                      <option value="deny">Deny</option>
                    </select>
                  </label>
                  <Button variant="secondary" disabled={Boolean(state.ctx.saving()) || state.locked()} onClick={state.addPermission}>
                    Add Override
                  </Button>
                </div>
                <div class="mini-list columns">
                  <For each={state.rules()}>
                    {(rule) => (
                      <article class="mini-item simple">
                        <strong>{rule.tool}</strong>
                        <span>{rule.pattern}</span>
                        <Tag>{rule.action}</Tag>
                      </article>
                    )}
                  </For>
                </div>
              </div>
            </section>
          </div>

          <Show when={state.panel() === "model"}>
            <div class="drawer-scrim" onClick={state.close} />
            <aside class="provider-drawer" aria-label="Agent model selector">
              <header class="drawer-header">
                <div>
                  <h2>Choose Model</h2>
                  <span>Favorites are listed first, then models are sorted alphabetically.</span>
                </div>
                <Button variant="ghost" aria-label="Close model selector" onClick={state.close}>
                  X
                </Button>
              </header>

              <SearchField
                class="drawer-search"
                hideLabel={false}
                label="Filter models"
                value={state.picker()}
                variant="drawer"
                placeholder="Search by name, provider, or ID"
                onValue={state.setPicker}
              />

              <div class="provider-picker model-picker">
                <Show when={state.models().length} fallback={<p class="empty">No models match this filter.</p>}>
                  <For each={state.models()}>
                    {(item) => (
                      <button
                        class="provider-option model-option"
                        classList={{ selected: state.choice() === item.id }}
                        type="button"
                        onClick={() => state.selectModel(item)}
                      >
                        <span class="model-star" classList={{ active: state.fav(item) }} aria-hidden="true" />
                        <div>
                          <strong>{item.model.name}</strong>
                          <span>{item.id}</span>
                        </div>
                        <div class="tags">
                          <Tag>{item.provider.name}</Tag>
                          <Tag>{item.model.isFree ? "free" : "paid"}</Tag>
                        </div>
                      </button>
                    )}
                  </For>
                </Show>
              </div>

              <footer class="drawer-footer">
                <Button variant="ghost" onClick={state.close}>
                  Cancel
                </Button>
                <Button
                  variant="primary"
                  disabled={Boolean(state.ctx.saving()) || state.locked() || !state.choice()}
                  onClick={state.saveModel}
                >
                  Save
                </Button>
              </footer>
            </aside>
          </Show>

          <Show when={state.panel() === "tools"}>
            <div class="drawer-scrim" onClick={state.close} />
            <aside class="provider-drawer" aria-label="Agent tool access selector">
              <header class="drawer-header">
                <div>
                  <h2>Choose Tool Access</h2>
                  <span>Selected tools are written as allow permissions in the agent frontmatter.</span>
                </div>
                <Button variant="ghost" aria-label="Close tool selector" onClick={state.close}>
                  X
                </Button>
              </header>

              <SearchField
                class="drawer-search"
                hideLabel={false}
                label="Filter tools"
                value={state.search()}
                variant="drawer"
                placeholder="Search by tool name, ID, or capability"
                onValue={state.setSearch}
              />

              <div class="provider-picker tool-picker">
                <Show when={state.options().length} fallback={<p class="empty">No tools match this filter.</p>}>
                  <For each={state.options()}>
                    {(tool) => (
                      <button
                        class="provider-option tool-option"
                        classList={{ selected: state.pickedDraft().has(tool.id) }}
                        aria-pressed={state.pickedDraft().has(tool.id)}
                        type="button"
                        onClick={() => state.toggleTool(tool.id)}
                      >
                        <div class="model-main tool-main">
                          <div class="model-title">
                            <div>
                              <strong>{toolName(tool.id)}</strong>
                              <span>{tool.id}</span>
                            </div>
                          </div>
                          <div class="tags">
                            <Tag>{state.pickedDraft().has(tool.id) ? "Allowed" : "Off"}</Tag>
                          </div>
                        </div>
                        <ul class="tool-capabilities">
                          <For each={toolCapabilities(tool)}>{(cap) => <li>{cap}</li>}</For>
                        </ul>
                      </button>
                    )}
                  </For>
                </Show>
              </div>

              <footer class="drawer-footer">
                <Button variant="ghost" onClick={state.close}>
                  Cancel
                </Button>
                <Button variant="primary" disabled={Boolean(state.ctx.saving()) || state.locked()} onClick={state.saveTools}>
                  Save
                </Button>
              </footer>
            </aside>
          </Show>

          <Show when={state.panel() === "markdown"}>
            <div class="drawer-scrim" onClick={state.close} />
            <aside class="provider-drawer" aria-label="Generated agent markdown">
              <header class="drawer-header">
                <div>
                  <h2>Markdown</h2>
                  <span>{state.draft()?.path ?? "Previewing the current agent configuration."}</span>
                </div>
                <Button variant="ghost" aria-label="Close markdown preview" onClick={state.close}>
                  X
                </Button>
              </header>

              <div class="provider-picker markdown-picker">
                <Show when={state.draft()} fallback={<p class="empty">Generating markdown preview...</p>}>
                  {(draft) => <pre class="markdown-preview">{draft().markdown}</pre>}
                </Show>
              </div>
            </aside>
          </Show>
        </ConfigPage>
      )}
    </Show>
  )
}
