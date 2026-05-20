import { For, Show } from "solid-js"
import { Button } from "@kilocode/kilo-ui/button"
import { Card } from "@kilocode/kilo-ui/card"
import { IconButton } from "@kilocode/kilo-ui/icon-button"
import { ProviderIcon } from "@kilocode/kilo-ui/provider-icon"
import { Tag } from "@kilocode/kilo-ui/tag"
import { ConfirmDialog } from "../../components/ConfirmDialog"
import { ConfigPage, ConfigToolbar, SourceBadge } from "./ConfigPage"
import { useProviderSettings } from "./state/providers"

export function ProvidersRoute() {
  const state = useProviderSettings()
  const project = () => state.ctx.query()?.scope === "project"

  return (
    <Show when={state.snap()}>
      {(data) => (
        <ConfigPage
          title="Providers"
          actions={
            <>
              <Button variant="primary" disabled={Boolean(state.ctx.saving()) || project()} onClick={state.add}>
                Add Provider
              </Button>
            </>
          }
        >
          <Show when={project()}>
            <Card class="banner" variant="info">
              Providers are global credentials. Project settings show inherited providers as read-only.
            </Card>
          </Show>

          <ConfigToolbar
            title="Configured Providers"
            description="Manage provider configurations for the selected scope."
            meta={<Tag>{`${state.configured().length} configured`}</Tag>}
          >
            <label>
              <span>Filter</span>
              <input
                type="search"
                value={state.search()}
                placeholder="Filter providers"
                onInput={(event) => state.setSearch(event.currentTarget.value)}
              />
            </label>
          </ConfigToolbar>

          <div class="providers">
            <Show when={state.visible().length} fallback={<p class="empty">No providers match this filter.</p>}>
              <For each={state.visible()}>
                {(provider) => (
                  <article class="provider configured-provider" classList={{ inherited: provider.inherited }}>
                    <div class="provider-title">
                      <ProviderIcon id={provider.id} class="provider-icon" />
                      <div>
                        <strong>{provider.name}</strong>
                        <span>{provider.id}</span>
                      </div>
                    </div>
                    <div class="tags">
                      <SourceBadge
                        source={provider.source}
                        inherited={provider.inherited}
                        overridden={provider.overridden}
                      />
                      <Tag>{`${provider.models} models`}</Tag>
                      <Show when={data().providers.connected.includes(provider.id)}>
                        <Tag>connected</Tag>
                      </Show>
                      <Show when={data().providers.failed.includes(provider.id)}>
                        <Tag>failed</Tag>
                      </Show>
                    </div>
                    <div class="provider-actions">
                      <IconButton
                        icon="edit"
                        variant="secondary"
                        aria-label={`Edit ${provider.name}`}
                        disabled={Boolean(state.ctx.saving()) || project() || provider.editable === false}
                        onClick={() => state.edit(provider)}
                      />
                      <IconButton
                        icon="trash"
                        variant="ghost"
                        aria-label={`Delete ${provider.name}`}
                        disabled={Boolean(state.ctx.saving()) || project() || provider.editable === false}
                        onClick={() => state.ask(provider)}
                      />
                    </div>
                  </article>
                )}
              </For>
            </Show>
          </div>

          <Show when={state.mode() !== "closed"}>
            <div class="drawer-scrim" onClick={state.close} />
            <aside class="provider-drawer" aria-label="Provider configuration">
              <Show
                when={state.mode() === "form"}
                fallback={
                  <>
                    <header class="drawer-header">
                      <div>
                        <h2>Available Providers</h2>
                        <span>Select a provider to configure.</span>
                      </div>
                      <Button variant="ghost" aria-label="Close provider overlay" onClick={state.close}>
                        X
                      </Button>
                    </header>
                    <label class="drawer-search">
                      Filter providers
                      <input
                        type="search"
                        value={state.filter()}
                        placeholder="Search by name or ID"
                        onInput={(event) => state.setFilter(event.currentTarget.value)}
                      />
                    </label>
                    <div class="provider-picker">
                      <Show
                        when={state.available().length}
                        fallback={<p class="empty">No available providers match.</p>}
                      >
                        <For each={state.available()}>
                          {(provider) => (
                            <button class="provider-option" type="button" onClick={() => state.pick(provider)}>
                              <ProviderIcon id={provider.id} class="provider-icon" />
                              <div>
                                <strong>{provider.name}</strong>
                                <span>{provider.id}</span>
                              </div>
                            </button>
                          )}
                        </For>
                      </Show>
                    </div>
                  </>
                }
              >
                <header class="drawer-header provider-config-header">
                  <div class="provider-title provider-drawer-title">
                    <ProviderIcon id={state.id() || state.selected()?.id || "synthetic"} class="provider-icon" />
                    <div>
                      <h2>{state.name() || state.selected()?.name || state.id() || "Provider"}</h2>
                      <span>{state.id() || "New provider"}</span>
                    </div>
                  </div>
                  <Button variant="ghost" aria-label="Close provider overlay" onClick={state.close}>
                    X
                  </Button>
                </header>

                <Show
                  when={state.auth()}
                  fallback={
                    <>
                      <div class="provider-form">
                        <label class="required-field">
                          Provider ID
                          <input
                            value={state.id()}
                            spellcheck={false}
                            onInput={(event) => state.setId(event.currentTarget.value)}
                          />
                        </label>
                        <label class="required-field">
                          Display name
                          <input value={state.name()} onInput={(event) => state.setName(event.currentTarget.value)} />
                        </label>
                        <label class="optional-field">
                          Environment variables
                          <input
                            value={state.env()}
                            placeholder="ANTHROPIC_API_KEY, OPENAI_API_KEY"
                            spellcheck={false}
                            onInput={(event) => state.setEnv(event.currentTarget.value)}
                          />
                        </label>
                        <label class="optional-field">
                          API key
                          <input
                            value={state.apiKey()}
                            placeholder="sk-... or {env:PROVIDER_API_KEY}"
                            spellcheck={false}
                            onInput={(event) => state.setApiKey(event.currentTarget.value)}
                          />
                        </label>
                        <label class="optional-field">
                          Base URL
                          <input
                            value={state.baseURL()}
                            placeholder="https://api.example.com/v1"
                            spellcheck={false}
                            onInput={(event) => state.setBaseURL(event.currentTarget.value)}
                          />
                        </label>
                        <label class="optional-field">
                          NPM package
                          <input
                            value={state.npm()}
                            placeholder="@ai-sdk/openai-compatible"
                            spellcheck={false}
                            onInput={(event) => state.setNpm(event.currentTarget.value)}
                          />
                        </label>
                        <label class="optional-field">
                          API identifier
                          <input
                            value={state.api()}
                            placeholder="openai-compatible"
                            spellcheck={false}
                            onInput={(event) => state.setApi(event.currentTarget.value)}
                          />
                        </label>
                        <label class="optional-field">
                          Model whitelist
                          <input
                            value={state.whitelist()}
                            placeholder="model-a, model-b"
                            spellcheck={false}
                            onInput={(event) => state.setWhitelist(event.currentTarget.value)}
                          />
                        </label>
                        <label class="optional-field">
                          Model blacklist
                          <input
                            value={state.blacklist()}
                            placeholder="model-a, model-b"
                            spellcheck={false}
                            onInput={(event) => state.setBlacklist(event.currentTarget.value)}
                          />
                        </label>
                        <label class="wide optional-field">
                          Extra options JSON
                          <textarea
                            value={state.options()}
                            spellcheck={false}
                            placeholder={'{\n  "timeout": 300000\n}'}
                            onInput={(event) => state.setOptions(event.currentTarget.value)}
                          />
                        </label>
                        <label class="wide optional-field">
                          Model overrides JSON
                          <textarea
                            value={state.models()}
                            spellcheck={false}
                            placeholder={'{\n  "model-id": { "name": "Model Name" }\n}'}
                            onInput={(event) => state.setModels(event.currentTarget.value)}
                          />
                        </label>
                      </div>

                      <footer class="drawer-footer">
                        <Button variant="secondary" onClick={state.close}>
                          Cancel
                        </Button>
                        <Button variant="primary" disabled={Boolean(state.ctx.saving())} onClick={state.save}>
                          Save Provider
                        </Button>
                      </footer>
                    </>
                  }
                >
                  <div class="provider-auth">
                    <Show when={state.methods().length > 1 && state.methodIndex() === undefined}>
                      <p>Select how to connect {state.name() || state.id()}.</p>
                      <div class="auth-methods">
                        <For each={state.methods()}>
                          {(method, index) => (
                            <Button variant="secondary" onClick={() => state.selectMethod(index())}>
                              {method.label}
                            </Button>
                          )}
                        </For>
                      </div>
                    </Show>

                    <Show when={state.phase() === "authorizing"}>
                      <p>Preparing authorization...</p>
                    </Show>

                    <Show when={state.method()?.type === "api"}>
                      <p>
                        Connect {state.name() || state.id()} with an API key. Extra fields are provided by the server.
                      </p>
                      <label>
                        API key
                        <input
                          type="password"
                          value={state.authKey()}
                          placeholder="Paste API key"
                          autocomplete="off"
                          spellcheck={false}
                          classList={{ invalid: state.authField() === "apiKey" }}
                          onInput={(event) => state.setAuthKey(event.currentTarget.value)}
                        />
                      </label>
                      <For each={state.prompts()}>
                        {(prompt) => (
                          <label>
                            {prompt.message}
                            <Show
                              when={prompt.type === "select"}
                              fallback={
                                <input
                                  value={state.fields()[prompt.key] ?? ""}
                                  placeholder={prompt.type === "text" ? prompt.placeholder : undefined}
                                  classList={{ invalid: state.authField() === prompt.key }}
                                  onInput={(event) => state.setField(prompt.key, event.currentTarget.value)}
                                />
                              }
                            >
                              <select
                                value={state.fields()[prompt.key] ?? ""}
                                classList={{ invalid: state.authField() === prompt.key }}
                                onInput={(event) => state.setField(prompt.key, event.currentTarget.value)}
                              >
                                <option value="">Select an option</option>
                                <For each={prompt.type === "select" ? prompt.options : []}>
                                  {(option) => (
                                    <option value={option.value}>
                                      {option.hint ? `${option.label} (${option.hint})` : option.label}
                                    </option>
                                  )}
                                </For>
                              </select>
                            </Show>
                          </label>
                        )}
                      </For>
                    </Show>

                    <Show when={state.authorization()?.method === "code"}>
                      <p>Open the authorization page and paste the code returned by {state.name() || state.id()}.</p>
                      <a href={state.authorization()?.url ?? "#"} target="_blank" rel="noreferrer">
                        Open authorization page
                      </a>
                      <label>
                        Authorization code
                        <input
                          value={state.authCode()}
                          placeholder="Paste authorization code"
                          classList={{ invalid: state.authField() === "code" }}
                          onInput={(event) => state.setAuthCode(event.currentTarget.value)}
                        />
                      </label>
                    </Show>

                    <Show when={state.authorization()?.method === "auto"}>
                      <p>
                        Complete authorization in the browser window. This page will refresh once the provider is
                        connected.
                      </p>
                      <Show when={state.authorization()?.instructions}>{(text) => <code>{text()}</code>}</Show>
                    </Show>

                    <Show when={state.authError()}>{(message) => <p class="form-error">{message()}</p>}</Show>
                  </div>

                  <footer class="drawer-footer">
                    <Button variant="secondary" onClick={state.close}>
                      Cancel
                    </Button>
                    <Show when={state.method()?.type === "api"}>
                      <Button variant="primary" disabled={Boolean(state.ctx.saving())} onClick={state.connectAuth}>
                        Save Provider
                      </Button>
                    </Show>
                    <Show when={state.authorization()?.method === "code"}>
                      <Button
                        variant="primary"
                        disabled={Boolean(state.ctx.saving())}
                        onClick={() => state.completeOAuth()}
                      >
                        Save Provider
                      </Button>
                    </Show>
                  </footer>
                </Show>
              </Show>
            </aside>
          </Show>
          <ConfirmDialog
            open={Boolean(state.pending())}
            title={`Delete provider ${state.pending()?.name ?? ""}?`}
            message={`This removes ${state.pending()?.id ?? "the provider"} from the current configuration.`}
            confirm="Delete"
            busy={Boolean(state.ctx.saving())}
            onCancel={state.cancel}
            onConfirm={state.confirm}
          />
        </ConfigPage>
      )}
    </Show>
  )
}
