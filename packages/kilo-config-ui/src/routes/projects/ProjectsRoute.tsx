import { createEffect, createMemo, createResource, createSignal, For, Show } from "solid-js"
import { A } from "@solidjs/router"
import { Card } from "@kilocode/kilo-ui/card"
import {
  discover,
  forgetCached,
  healthy,
  loadCached,
  loadVisibleProjects,
  saveCached,
  type ProjectItem,
  type ProjectQuery,
} from "../../client"
import { clean, errMsg, friendly } from "../../shared/utils"

const search = new URLSearchParams(window.location.search)
const ui = new Set(["3017", "3018"])

function discoverable() {
  if (search.get("server")) return false
  return ui.has(window.location.port)
}

function base() {
  const param = search.get("server")
  if (param) return param
  const cached = discoverable() ? loadCached() : ""
  if (cached) return cached
  if (discoverable()) return ""
  return window.location.origin
}

function repo(input: string) {
  const parts = input.split(/[\\/]/).filter(Boolean)
  return parts.at(-1) ?? "Global"
}

function name(item: ProjectItem) {
  return friendly(item.name?.trim() || repo(item.worktree) || item.id.slice(0, 8))
}

function mark(item: ProjectItem) {
  const parts = name(item)
    .split(/[\s._/-]+/)
    .filter(Boolean)
  const text = `${parts[0]?.[0] ?? ""}${parts[1]?.[0] ?? parts[0]?.[1] ?? ""}`
  return text.toUpperCase() || "KG"
}

function color(item: ProjectItem) {
  const hue = [...(item.id || name(item))].reduce((sum, char) => sum + char.charCodeAt(0), 0) % 360
  return `hsl(${hue} 78% 42%)`
}

function git(item: ProjectItem) {
  if (item.vcs === "git") return repo(item.worktree)
  return "No git repository"
}

function server(input: string | undefined) {
  if (!input) return "Discovering server"
  if (input.includes("127.0.0.1")) return "Local"
  return input
}

function href(item: ProjectItem) {
  const params = new URLSearchParams()
  const server = search.get("server")
  if (server) params.set("server", server)
  const query = params.toString()
  return `/projects/${encodeURIComponent(item.id)}/settings${query ? `?${query}` : ""}`
}

export function ProjectsRoute() {
  const [url, setUrl] = createSignal(base())
  const query = createMemo<ProjectQuery | undefined>(() => {
    const target = clean(url()) || base()
    if (!target) return undefined
    return { url: target, dir: "" }
  })
  const [items] = createResource(query, loadVisibleProjects)
  const rows = createMemo(() => [...(items() ?? [])].sort((a, b) => b.time.updated - a.time.updated))

  createEffect(() => {
    if (!discoverable()) return
    const cached = loadCached()
    void Promise.resolve(cached ? healthy(cached) : false)
      .then((ok) => {
        if (ok) return cached
        forgetCached()
        return discover()
      })
      .then((value) => {
        if (!value) return
        saveCached(value)
        setUrl(value)
      })
  })

  createEffect(() => {
    const current = query()
    if (!items() || !current || !discoverable()) return
    saveCached(current.url)
  })

  createEffect(() => {
    if (!items.error || !discoverable()) return
    const cached = loadCached()
    if (!cached || cached !== url()) return
    forgetCached()
    setUrl("")
    void discover().then((value) => {
      if (!value) return
      saveCached(value)
      setUrl(value)
    })
  })

  return (
    <section class="route-empty">
      <p class="eyebrow">Projects</p>
      <h1>All Projects</h1>
      <p>Projects opened with Kilo on the connected server.</p>

      <Show when={!query() && discoverable()}>
        <Card class="banner" variant="info">
          Discovering Kilo server...
        </Card>
      </Show>

      <Show when={items.loading && !items()}>
        <Card class="banner" variant="info">
          Loading projects...
        </Card>
      </Show>

      <Show when={items.error}>
        <Card class="banner" variant="error">
          <strong>Project request failed</strong>
          <span>{errMsg(items.error)}</span>
        </Card>
      </Show>

      <Show when={query() && !items.loading && rows().length === 0 && !items.error}>
        <Card class="empty">No projects have been opened with this Kilo server yet.</Card>
      </Show>

      <div class="project-list" role="list" aria-label="Kilo projects">
        <For each={rows()}>
          {(item) => (
            <A class="project-row-link" href={href(item)}>
              <Card class="project-row" role="listitem">
                <div class="project-icon" style={{ "--project-color": color(item) }} aria-hidden="true">
                  {mark(item)}
                </div>
                <div class="project-body">
                  <div class="project-title">
                    <strong>{name(item)}</strong>
                    <span>{item.id}</span>
                  </div>
                  <div class="project-fields">
                    <div class="project-field">
                      <span>Git repo</span>
                      <strong>{git(item)}</strong>
                    </div>
                    <div class="project-field">
                      <span>Server</span>
                      <strong>{server(query()?.url)}</strong>
                    </div>
                    <div class="project-field project-folder">
                      <span>Folder</span>
                      <strong>{item.worktree}</strong>
                    </div>
                  </div>
                </div>
              </Card>
            </A>
          )}
        </For>
      </div>
    </section>
  )
}
