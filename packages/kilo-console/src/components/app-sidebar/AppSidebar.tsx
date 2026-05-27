import { A, useLocation } from "@solidjs/router"
import { createEffect, createMemo, createResource, createSignal, For, onCleanup } from "solid-js"
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

function Glyph(props: { name: "projects" | "settings" | "profile" }) {
  if (props.name === "projects") {
    return (
      <svg class="rail-glyph" viewBox="0 0 24 24" aria-hidden="true" fill="none">
        <path d="M5 4h4l3 3h7a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-11a2 2 0 0 1 2 -2" />
      </svg>
    )
  }

  if (props.name === "settings") {
    return (
      <svg class="rail-glyph" viewBox="0 0 24 24" aria-hidden="true" fill="none">
        <path d="M10.325 4.317c.426 -1.756 2.924 -1.756 3.35 0a1.724 1.724 0 0 0 2.573 1.066c1.543 -.94 3.31 .826 2.37 2.37a1.724 1.724 0 0 0 1.065 2.572c1.756 .426 1.756 2.924 0 3.35a1.724 1.724 0 0 0 -1.066 2.573c.94 1.543 -.826 3.31 -2.37 2.37a1.724 1.724 0 0 0 -2.572 1.065c-.426 1.756 -2.924 1.756 -3.35 0a1.724 1.724 0 0 0 -2.573 -1.066c-1.543 .94 -3.31 -.826 -2.37 -2.37a1.724 1.724 0 0 0 -1.065 -2.572c-1.756 -.426 -1.756 -2.924 0 -3.35a1.724 1.724 0 0 0 1.066 -2.573c-.94 -1.543 .826 -3.31 2.37 -2.37.996 .608 2.296 .07 2.572 -1.065z" />
        <path d="M9 12a3 3 0 1 0 6 0a3 3 0 0 0 -6 0" />
      </svg>
    )
  }

  return (
    <svg class="rail-glyph" viewBox="0 0 24 24" aria-hidden="true" fill="none">
      <path d="M8 7a4 4 0 1 0 8 0a4 4 0 0 0 -8 0" />
      <path d="M6 21v-2a4 4 0 0 1 4 -4h4a4 4 0 0 1 4 4v2" />
    </svg>
  )
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
  const selected = (item: RecentProjectItem) => {
    return loc.pathname.startsWith(`/projects/${encodeURIComponent(item.id)}/`)
  }
  const nav = () => [
    { href: "/projects", label: "Projects", name: "projects", path: "/projects" },
  ] as const
  const bottom = () => [
    { href: "/profile", label: "Profile", name: "profile", path: "/profile" },
    { href: settings(), label: "Settings", name: "settings", path: "/settings" },
  ] as const

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
      <nav class="rail-nav" aria-label="Primary">
        <For each={nav()}>
          {(item) => (
            <A
              class="rail-action"
              classList={{ active: props.path === item.path }}
              href={item.href}
              aria-label={item.label}
              aria-current={props.path === item.path ? "page" : undefined}
              title={item.label}
            >
              <Glyph name={item.name} />
            </A>
          )}
        </For>
      </nav>

      <div class="rail-favorites" aria-label="Projects with recent sessions">
        <For each={items() ?? []}>
          {(item) => (
            <A
              class="favorite-project"
              classList={{ active: selected(item) }}
              href={href(item, params())}
              aria-label={name(item)}
              aria-current={selected(item) ? "page" : undefined}
              title={name(item)}
            >
              {mark(item)}
            </A>
          )}
        </For>
      </div>

      <nav class="rail-bottom" aria-label="Account and settings">
        <For each={bottom()}>
          {(item) => (
            <A
              class="rail-action"
              classList={{ active: props.path === item.path }}
              href={item.href}
              aria-label={item.label}
              aria-current={props.path === item.path ? "page" : undefined}
              title={item.label}
            >
              <Glyph name={item.name} />
            </A>
          )}
        </For>
      </nav>
    </aside>
  )
}
