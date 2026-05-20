import { For, Show } from "solid-js"
import { Button } from "@kilocode/kilo-ui/button"
import { Tag } from "@kilocode/kilo-ui/tag"
import { toMode, toAction } from "../../shared/utils"
import { ConfigPage, SourceBadge } from "./ConfigPage"
import { snippets, useAgentBuilder } from "./state/agents"

export function AgentsRoute() {
  const state = useAgentBuilder()

  return (
    <Show when={state.snap()}>
      {(data) => (
        <ConfigPage title="Agent Builder" actions={<Tag>{data().agents.length}</Tag>}>
          <div class="builder">
            <section class="builder-form">
              <div class="builder-grid">
                <label>
                  Scope
                  <select
                    value={state.scope()}
                    onChange={(event) => state.setScope(event.currentTarget.value === "global" ? "global" : "project")}
                  >
                    <option value="project">Project</option>
                    <option value="global">Global</option>
                  </select>
                </label>
                <label>
                  Agent id
                  <input
                    value={state.id()}
                    placeholder="reviewer"
                    spellcheck={false}
                    onInput={(event) => state.setId(event.currentTarget.value)}
                  />
                </label>
                <label>
                  Mode
                  <select value={state.mode()} onChange={(event) => state.setMode(toMode(event.currentTarget.value))}>
                    <option value="primary">Primary</option>
                    <option value="subagent">Subagent</option>
                    <option value="all">Both</option>
                  </select>
                </label>
                <label>
                  Model
                  <input
                    list="agent-model-list"
                    value={state.model()}
                    placeholder="provider/model or inherit default"
                    spellcheck={false}
                    onInput={(event) => state.setModel(event.currentTarget.value)}
                  />
                </label>
                <label>
                  Description
                  <input
                    value={state.desc()}
                    placeholder="Review code and report risks"
                    onInput={(event) => state.setDesc(event.currentTarget.value)}
                  />
                </label>
                <label>
                  Color
                  <input
                    value={state.color()}
                    placeholder="blue"
                    onInput={(event) => state.setColor(event.currentTarget.value)}
                  />
                </label>
                <label>
                  Max steps
                  <input
                    value={state.steps()}
                    placeholder="optional"
                    inputMode="numeric"
                    onInput={(event) => state.setSteps(event.currentTarget.value)}
                  />
                </label>
                <label class="wide">
                  Tool ids
                  <input
                    value={state.tools().join(", ")}
                    placeholder="read, grep, bash"
                    spellcheck={false}
                    onInput={(event) => state.setTools(event.currentTarget.value)}
                  />
                </label>
                <label class="wide prompt-field">
                  Prompt
                  <textarea
                    value={state.prompt()}
                    placeholder="Describe how this agent should behave."
                    onInput={(event) => state.setPrompt(event.currentTarget.value)}
                  />
                </label>
              </div>
              <datalist id="agent-model-list">
                <For each={state.all()}>{(item) => <option value={item.id}>{item.model.name}</option>}</For>
              </datalist>

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
                        disabled={Boolean(state.ctx.saving())}
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
                  <strong>Tool Access</strong>
                  <span>Selected tools are written as allow permissions in the agent frontmatter.</span>
                </div>
                <div class="tool-picks">
                  <For each={data().tools.slice(0, 32)}>
                    {(tool) => (
                      <Button
                        variant={state.picked().has(tool) ? "primary" : "secondary"}
                        disabled={Boolean(state.ctx.saving())}
                        onClick={() => state.toggleTool(tool)}
                      >
                        {tool}
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
                      onInput={(event) => state.setPermTool(event.currentTarget.value)}
                    />
                  </label>
                  <label>
                    Pattern
                    <input
                      value={state.permPattern()}
                      placeholder="optional pattern"
                      onInput={(event) => state.setPermPattern(event.currentTarget.value)}
                    />
                  </label>
                  <label>
                    Action
                    <select
                      value={state.permAction()}
                      onChange={(event) => state.setPermAction(toAction(event.currentTarget.value))}
                    >
                      <option value="ask">Ask</option>
                      <option value="allow">Allow</option>
                      <option value="deny">Deny</option>
                    </select>
                  </label>
                  <Button variant="secondary" disabled={Boolean(state.ctx.saving())} onClick={state.addPermission}>
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

              <div class="builder-actions">
                <Button variant="secondary" disabled={Boolean(state.ctx.saving())} onClick={state.preview}>
                  Preview Markdown
                </Button>
                <Button variant="primary" disabled={Boolean(state.ctx.saving())} onClick={state.save}>
                  Save Agent
                </Button>
              </div>
            </section>

            <section class="builder-side">
              <div class="builder-block">
                <div class="block-title">
                  <strong>Existing Agents</strong>
                  <span>Reloads after save so the CLI selector sees the new file.</span>
                </div>
                <div class="mini-list">
                  <Show when={data().agents.length} fallback={<p class="empty">No agents loaded.</p>}>
                    <For each={data().agents.slice(0, 12)}>
                      {(item) => {
                        const meta = () => data().overlay.collections.agent.find((entry) => entry.key === item.name)
                        return (
                          <article class="mini-item simple" classList={{ inherited: meta()?.inherited }}>
                            <strong>{item.displayName ?? item.name}</strong>
                            <span>{item.description ?? item.name}</span>
                            <div class="tags">
                              <Tag>{item.mode}</Tag>
                              <Show when={meta()}>
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
                        )
                      }}
                    </For>
                  </Show>
                </div>
              </div>

              <div class="builder-block preview-card">
                <div class="block-title">
                  <strong>Markdown Preview</strong>
                  <span>{state.draft()?.path ?? "Preview or save to generate a path."}</span>
                </div>
                <Show when={state.draft()} fallback={<p class="empty">No preview generated yet.</p>}>
                  {(draft) => <pre class="markdown-preview">{draft().markdown}</pre>}
                </Show>
              </div>
            </section>
          </div>
        </ConfigPage>
      )}
    </Show>
  )
}
