import { For, Show } from "solid-js"
import { Button } from "@kilocode/kilo-web-ui/button"
import { IconButton } from "@kilocode/kilo-web-ui/icon-button"
import { Tag } from "@kilocode/kilo-web-ui/tag"
import type { Model } from "@kilocode/sdk/v2/client"
import { SearchField } from "../../components/SearchField"
import { text } from "../../shared/utils"
import { ConfigPage, ConfigToolbar, SourceBadge } from "./ConfigPage"
import { type Capability, type ModelField, useModelSettings } from "./state/models"

function fmtPrice(n: number) {
  if (n === 0) return "Free"
  if (n < 0.01) return `$${n.toFixed(4)}/1M`
  return `$${n.toFixed(2)}/1M`
}

function fmtCachedPrice(cost: Model["cost"]) {
  if (cost.cache.read > 0) return fmtPrice(cost.cache.read)
  if (cost.input === 0) return fmtPrice(0)
  return null
}

function avgPrice(cost: Model["cost"]) {
  if (cost.cache.read > 0) return cost.cache.read * 0.7 + cost.input * 0.2 + cost.output * 0.1
  return cost.input * 0.9 + cost.output * 0.1
}

function fmtContext(n: number) {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(n % 1_000_000 === 0 ? 0 : 1)}M`
  if (n >= 1_000) return `${(n / 1_000).toFixed(n % 1_000 === 0 ? 0 : 1)}K`
  return String(n)
}

function fmtDate(s: string) {
  const d = new Date(s)
  if (isNaN(d.getTime())) return s
  return d.toLocaleDateString(undefined, { year: "numeric", month: "short" })
}

function title(input: string) {
  return input.charAt(0).toUpperCase() + input.slice(1)
}

function mods(input: Record<string, boolean>) {
  const values = Object.entries(input)
    .filter(([key, value]) => value && key !== "text")
    .map(([key]) => title(key))
  return values.length > 0 ? values.join(", ") : "Text only"
}

function desc(model: Model) {
  const value = model.options?.description
  if (typeof value === "string" && value.trim()) return value
  return null
}

function capLabel(cap: Capability) {
  if (cap === "toolcall") return "Tools"
  if (cap === "attachment") return "Attachments"
  if (cap === "temperature") return "Temperature"
  if (cap === "interleaved") return "Interleaved"
  if (cap === "input:audio") return "Input audio"
  if (cap === "input:image") return "Input image"
  if (cap === "input:video") return "Input video"
  if (cap === "input:pdf") return "Input PDF"
  if (cap === "output:audio") return "Output audio"
  if (cap === "output:image") return "Output image"
  if (cap === "output:video") return "Output video"
  return "Output PDF"
}

function step(max: number) {
  if (max >= 1_000_000) return 50_000
  if (max >= 100_000) return 10_000
  return 1_000
}

const slots = [
  { key: "model", label: "Default model" },
  { key: "small_model", label: "Small model" },
] satisfies { key: ModelField; label: string }[]

function pct(value: number, max: number) {
  if (max <= 0) return 0
  return Math.max(0, Math.min(100, (value / max) * 100))
}

function snap(value: number, max: number) {
  const size = step(max)
  return Math.max(0, Math.min(max, Math.round(value / size) * size))
}

function point(event: PointerEvent, root: HTMLElement, max: number) {
  const rect = root.getBoundingClientRect()
  const ratio = (event.clientX - rect.left) / rect.width
  return snap(ratio * max, max)
}

function rangeLabel(low: number, high: number, max: number) {
  const top = high >= max && max > 0 ? `${fmtContext(high)}+` : fmtContext(high)
  return `${fmtContext(low)} - ${top}`
}

export function ModelsRoute() {
  return <ModelsDefaultRoute />
}

export function ModelsDefaultRoute() {
  const state = useModelSettings()

  return (
    <Show when={state.snap()}>
      <ConfigPage title="Defaults" actions={<Tag>{`${state.providers().length} providers`}</Tag>}>
        <div class="resolved-grid model-defaults">
          <For each={slots}>
            {(item) => {
              const field = () => state.snap()?.overlay.fields[item.key]
              const model = () => state.item(field()?.value)
              return (
                <article class="resolved-card default-model-card" classList={{ inherited: field()?.inherited }}>
                  <header class="default-model-header">
                    <span>{item.label}</span>
                    <div class="tags default-model-actions">
                      <SourceBadge
                        source={field()?.source}
                        inherited={field()?.inherited}
                        overridden={field()?.overridden}
                      />
                      <Show when={state.ctx.query()?.scope === "project" && field()?.overridden}>
                        <Button
                          variant="secondary"
                          disabled={Boolean(state.ctx.saving())}
                          onClick={() => state.ctx.unset([[item.key]])}
                        >
                          Revert
                        </Button>
                      </Show>
                      <IconButton
                        icon="edit"
                        variant="secondary"
                        aria-label={`Edit ${item.label}`}
                        disabled={Boolean(state.ctx.saving())}
                        onClick={() => state.edit(item.key)}
                      />
                    </div>
                  </header>
                  <div class="default-model-value">
                    <Show when={model()} fallback={<strong>{text(field()?.value)}</strong>}>
                      {(selected) => (
                        <>
                          <strong>{`${selected().provider.name} / ${selected().model.name}`}</strong>
                          <span class="default-model-id">{selected().id}</span>
                        </>
                      )}
                    </Show>
                  </div>
                </article>
              )
            }}
          </For>
        </div>

        <Show when={state.mode() !== "closed"}>
          <div class="drawer-scrim" onClick={state.close} />
          <aside class="provider-drawer" aria-label={`${state.label()} selector`}>
            <header class="drawer-header">
              <div>
                <h2>{`Choose ${state.label()}`}</h2>
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
              <Show when={state.options().length} fallback={<p class="empty">No models match this filter.</p>}>
                <For each={state.options()}>
                  {(item) => (
                    <button
                      class="provider-option model-option"
                      classList={{ selected: state.choice() === item.id }}
                      type="button"
                      onClick={() => state.select(item)}
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
              <Button variant="secondary" onClick={state.close}>
                Cancel
              </Button>
              <Button variant="primary" disabled={Boolean(state.ctx.saving()) || !state.choice()} onClick={state.save}>
                Save
              </Button>
            </footer>
          </aside>
        </Show>
      </ConfigPage>
    </Show>
  )
}

export function ModelsAvailableRoute() {
  const state = useModelSettings()

  function drag(kind: "min" | "max", event: PointerEvent & { currentTarget: HTMLButtonElement }) {
    const root = event.currentTarget.closest(".context-range")
    if (!(root instanceof HTMLElement)) return
    event.preventDefault()
    event.currentTarget.setPointerCapture(event.pointerId)
    const move = (next: PointerEvent) => {
      const value = point(next, root, state.max())
      if (kind === "min") {
        state.setMin(value)
        return
      }
      state.setMax(value)
    }
    const up = () => {
      window.removeEventListener("pointermove", move)
      window.removeEventListener("pointerup", up)
    }
    move(event)
    window.addEventListener("pointermove", move)
    window.addEventListener("pointerup", up, { once: true })
  }

  function key(kind: "min" | "max", event: KeyboardEvent) {
    const delta = event.key === "ArrowLeft" ? -step(state.max()) : event.key === "ArrowRight" ? step(state.max()) : 0
    if (delta === 0) return
    event.preventDefault()
    if (kind === "min") {
      state.setMin(state.low() + delta)
      return
    }
    state.setMax(state.top() + delta)
  }

  return (
    <Show when={state.snap()}>
      <ConfigPage title="Explore" actions={<Tag>{state.models().length}</Tag>}>
        <ConfigToolbar title="Model Filters" description="Search and filter configured provider models.">
          <label>
            <span>Search</span>
            <input
              value={state.search()}
              placeholder="Model name"
              onInput={(event) => state.setSearch(event.currentTarget.value)}
            />
          </label>
          <label>
            <span>Provider</span>
            <select value={state.filter()} onChange={(event) => state.setFilter(event.currentTarget.value)}>
              <option value="all">All providers</option>
              <For each={state.providers()}>{(provider) => <option value={provider.id}>{provider.name}</option>}</For>
            </select>
          </label>
          <label>
            <span>Cost</span>
            <select value={state.price()} onChange={(event) => state.setPrice(event.currentTarget.value)}>
              <option value="all">Free and paid</option>
              <option value="free">Free only</option>
              <option value="paid">Paid only</option>
            </select>
          </label>
          <label>
            <span>Reasoning</span>
            <select value={state.reason()} onChange={(event) => state.setReason(event.currentTarget.value)}>
              <option value="all">Any reasoning</option>
              <option value="reasoning">Reasoning only</option>
              <option value="standard">No reasoning</option>
            </select>
          </label>
          <label>
            <span>Favorites</span>
            <select
              value={state.starred() ? "favorites" : "all"}
              onChange={(event) => state.setStarred(event.currentTarget.value === "favorites")}
            >
              <option value="all">All models</option>
              <option value="favorites">Favorites only</option>
            </select>
          </label>
          <label class="context-filter">
            <span>Context</span>
            <div
              class="context-range"
              style={`--context-min: ${pct(state.low(), state.max())}%; --context-max: ${pct(state.top(), state.max())}%;`}
            >
              <span class="context-track" aria-hidden="true" />
              <span class="context-fill" aria-hidden="true" />
              <button
                class="context-min"
                aria-label="Minimum context"
                aria-valuemin="0"
                aria-valuemax={state.top()}
                aria-valuenow={state.low()}
                disabled={state.max() === 0}
                role="slider"
                type="button"
                onKeyDown={(event) => key("min", event)}
                onPointerDown={(event) => drag("min", event)}
              />
              <button
                class="context-max"
                aria-label="Maximum context"
                aria-valuemin={state.low()}
                aria-valuemax={state.max()}
                aria-valuenow={state.top()}
                disabled={state.max() === 0}
                role="slider"
                type="button"
                onKeyDown={(event) => key("max", event)}
                onPointerDown={(event) => drag("max", event)}
              />
            </div>
            <small>{rangeLabel(state.low(), state.top(), state.max())}</small>
          </label>
          <details class="multi-check">
            <summary>{state.caps().length ? `${state.caps().length} capabilities` : "Capabilities"}</summary>
            <div class="multi-check-menu">
              <For each={state.capabilities}>
                {(cap) => (
                  <label>
                    <input type="checkbox" checked={state.caps().includes(cap)} onChange={() => state.toggle(cap)} />
                    <span>{capLabel(cap)}</span>
                  </label>
                )}
              </For>
            </div>
          </details>
        </ConfigToolbar>

        <div class="models">
          <Show when={state.models().length} fallback={<p class="empty">No models match the current filters.</p>}>
            <For each={state.models()}>
              {(item) => (
                <article class="model">
                  <div class="model-main">
                    <div class="model-title">
                      <button
                        aria-label={state.fav(item) ? "Remove favorite" : "Add favorite"}
                        aria-pressed={state.fav(item)}
                        class="model-star"
                        classList={{ active: state.fav(item) }}
                        onClick={() => state.favorite(item)}
                        type="button"
                      />
                      <div>
                        <strong>{item.model.name}</strong>
                        <span>{item.id}</span>
                      </div>
                    </div>
                    <div class="tags">
                      <Tag>{item.provider.name}</Tag>
                      <Tag>{item.model.isFree ? "free" : "paid"}</Tag>
                    </div>
                  </div>
                  <Show when={desc(item.model)}>{(value) => <p class="model-description">{value()}</p>}</Show>
                  <div class="model-info-grid">
                    <Show when={item.model.family}>
                      <div class="model-stat">
                        <span>Family</span>
                        <strong>{title(item.model.family!)}</strong>
                      </div>
                    </Show>
                    <Show when={item.model.release_date}>
                      <div class="model-stat">
                        <span>Released</span>
                        <strong>{fmtDate(item.model.release_date)}</strong>
                      </div>
                    </Show>
                    <div class="model-stat">
                      <span>Input</span>
                      <strong>{fmtPrice(item.model.cost.input)}</strong>
                    </div>
                    <div class="model-stat">
                      <span>Output</span>
                      <strong>{fmtPrice(item.model.cost.output)}</strong>
                    </div>
                    <Show when={fmtCachedPrice(item.model.cost)}>
                      {(value) => (
                        <div class="model-stat">
                          <span>Cached</span>
                          <strong>{value()}</strong>
                        </div>
                      )}
                    </Show>
                    <div class="model-stat">
                      <span>Avg Cost</span>
                      <strong>{fmtPrice(avgPrice(item.model.cost))}</strong>
                    </div>
                    <div class="model-stat">
                      <span>Context</span>
                      <strong>{fmtContext(item.model.limit.context)}</strong>
                    </div>
                    <div class="model-stat">
                      <span>Reasoning</span>
                      <strong>{item.model.capabilities.reasoning ? "Yes" : "No"}</strong>
                    </div>
                    <div class="model-stat">
                      <span>Input caps</span>
                      <strong>{mods(item.model.capabilities.input)}</strong>
                    </div>
                    <div class="model-stat">
                      <span>Output caps</span>
                      <strong>{mods(item.model.capabilities.output)}</strong>
                    </div>
                  </div>
                </article>
              )}
            </For>
          </Show>
        </div>
      </ConfigPage>
    </Show>
  )
}
