import { A, useLocation } from "@solidjs/router"
import { createEffect, createMemo, createResource, createSignal, For, onCleanup } from "solid-js"
import { IconButton } from "@kilocode/kilo-ui/icon-button"
import {
  discover,
  forgetCached,
  healthy,
  loadRecentProjects,
  loadCached,
  saveCached,
  type RecentProjectItem,
  type ProjectQuery,
} from "../../client"
import { type Path } from "../../shared/navigation"
import { clean, friendly } from "../../shared/utils"

const ports = new Set(["3017", "3018"])

function shouldDiscover(input: URLSearchParams) {
  if (input.get("server")) return false
  return ports.has(window.location.port)
}

function base(input: URLSearchParams) {
  const param = input.get("server")
  if (param) return param
  const cached = shouldDiscover(input) ? loadCached() : ""
  if (cached) return cached
  if (shouldDiscover(input)) return ""
  return window.location.origin
}

function repo(input: string) {
  const parts = input.split(/[\\/]/).filter(Boolean)
  return parts.at(-1) ?? "Global"
}

function name(item: RecentProjectItem) {
  return friendly(item.name?.trim() || repo(item.worktree) || item.id.slice(0, 8))
}

function mark(item: RecentProjectItem) {
  const parts = name(item)
    .split(/[\s._/-]+/)
    .filter(Boolean)
  const text = `${parts[0]?.[0] ?? ""}${parts[1]?.[0] ?? parts[0]?.[1] ?? ""}`
  return text.toUpperCase() || "KG"
}

function tail(input: URLSearchParams) {
  const next = new URLSearchParams(input)
  next.delete("directory")
  const query = next.toString()
  return query ? `?${query}` : ""
}

function href(item: RecentProjectItem, input: URLSearchParams) {
  return `/projects/${encodeURIComponent(item.id)}/settings${tail(input)}`
}

type Props = {
  path: Path
}

export function AppSidebar(props: Props) {
  const loc = useLocation()
  const params = createMemo(() => new URLSearchParams(loc.search))
  const discoverable = () => shouldDiscover(params())
  const fallback = () => base(params())
  const [url, setUrl] = createSignal(fallback())
  const query = createMemo<ProjectQuery | undefined>(() => {
    const target = clean(url()) || fallback()
    if (!target) return undefined
    return { url: target, dir: "" }
  })
  const [items, { refetch }] = createResource(query, loadRecentProjects)
  const settings = () => {
    return `/settings${tail(params())}`
  }

  createEffect(() => {
    const next = params().get("server")
    if (next && next !== url()) setUrl(next)
  })

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

  createEffect(() => {
    if (!query()) return
    const timer = window.setInterval(() => void refetch(), 5000)
    onCleanup(() => window.clearInterval(timer))
  })

  return (
    <aside class="rail" aria-label="Primary navigation">
      <A class="rail-action" classList={{ active: props.path === "/projects" }} href="/projects" aria-label="Projects">
        <IconButton icon="folder" variant="ghost" tabindex={-1} />
      </A>

      <div class="rail-favorites" aria-label="Projects with recent sessions">
        <For each={items() ?? []}>
          {(item) => (
            <A class="favorite-project" href={href(item, params())} aria-label={name(item)} title={name(item)}>
              {mark(item)}
            </A>
          )}
        </For>
      </div>

      <div class="rail-bottom">
        <A class="rail-action" classList={{ active: props.path === "/profile" }} href="/profile" aria-label="Profile">
          <IconButton icon="organization" variant="ghost" tabindex={-1} />
        </A>
        <A
          class="rail-action"
          classList={{ active: props.path === "/settings" }}
          href={settings()}
          aria-label="Settings"
        >
          <IconButton icon="settings-gear" variant="ghost" tabindex={-1} />
        </A>
      </div>
    </aside>
  )
}
