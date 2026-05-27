import { A, useLocation, useParams } from "@solidjs/router"
import { createEffect, createMemo, createResource, createSignal, For, Show } from "solid-js"
import { Card } from "@kilocode/kilo-web-ui/card"
import {
  createProjectPty,
  createProjectWorktree,
  discover,
  forgetCached,
  healthy,
  loadCached,
  loadProjectConsole,
  loadProjectDiff,
  loadProjectDiffFile,
  removeProjectWorktree,
  resetProjectWorktree,
  saveCached,
  type ProjectConsoleQuery,
  type ProjectTerminalItem,
  type Query,
} from "../../client"
import { clean, errMsg, friendly } from "../../shared/utils"
import { GhosttyTerminal } from "./terminal/GhosttyTerminal"

const ui = new Set(["3017", "3018"])

type Context = {
  id: string
  dir: string
  label: string
  kind: "local" | "worktree"
}

function discoverable(search: URLSearchParams) {
  if (search.get("server")) return false
  return ui.has(window.location.port)
}

function base(search: URLSearchParams) {
  const param = search.get("server")
  if (param) return param
  const cached = discoverable(search) ? loadCached() : ""
  if (cached) return cached
  if (discoverable(search)) return ""
  return window.location.origin
}

function repo(input: string) {
  const parts = input.split(/[\\/]/).filter(Boolean)
  return parts.at(-1) ?? "Project"
}

function title(input: string) {
  return friendly(repo(input))
}

export function ProjectConsoleRoute() {
  const loc = useLocation()
  const params = useParams()
  const search = createMemo(() => new URLSearchParams(loc.search))
  const fallback = () => base(search())
  const [url, setUrl] = createSignal(fallback())
  const [selected, setSelected] = createSignal(window.localStorage.getItem(`kilo.console.${params.project}.dir`) ?? "")
  const [active, setActive] = createSignal(window.localStorage.getItem(`kilo.console.${params.project}.pty`) ?? "")
  const [local, setLocal] = createSignal<ProjectTerminalItem[]>([])
  const [file, setFile] = createSignal<string | undefined>()
  const [saving, setSaving] = createSignal<string | undefined>()
  const [failure, setFailure] = createSignal<string | undefined>()
  const project = () => params.project ?? ""
  const query = createMemo<ProjectConsoleQuery | undefined>(() => {
    const target = clean(url()) || fallback()
    if (!target || !project()) return undefined
    return { url: target, dir: "", project: project() }
  })
  const [snap, { refetch }] = createResource(query, loadProjectConsole)

  const contexts = createMemo<Context[]>(() => {
    const data = snap()
    if (!data) return []
    return [
      { id: "local", dir: data.project.worktree, label: "Local", kind: "local" },
      ...data.worktrees.map((dir) => ({ id: dir, dir, label: title(dir), kind: "worktree" as const })),
    ]
  })
  const terminals = createMemo(() => {
    const items = new Map<string, ProjectTerminalItem>()
    for (const item of snap()?.terminals ?? []) {
      if (item.status === "running") items.set(item.id, item)
    }
    for (const item of local()) {
      if (item.status === "running") items.set(item.id, item)
    }
    return Array.from(items.values())
  })
  const grouped = createMemo(() => {
    const items = new Map<string, ProjectTerminalItem[]>()
    for (const item of terminals()) {
      const list = items.get(item.directory) ?? []
      list.push(item)
      items.set(item.directory, list)
    }
    return items
  })
  const current = createMemo(() => contexts().find((item) => item.dir === selected()) ?? contexts()[0])
  const activeTerminal = createMemo(() => terminals().find((item) => item.id === active()))
  const target = createMemo<Query | undefined>(() => {
    const data = snap()
    const item = current()
    const base = query()
    if (!data || !item || !base) return undefined
    return { url: base.url, dir: item.dir, scope: "project" }
  })
  const diffKey = createMemo(() => {
    const item = target()
    if (!item) return undefined
    return { input: item, dir: item.dir }
  })
  const [diffs] = createResource(diffKey, (item) => loadProjectDiff(item.input, item.dir))
  const detailKey = createMemo(() => {
    const item = target()
    const path = file()
    if (!item || !path) return undefined
    return { input: item, dir: item.dir, file: path }
  })
  const terminal = createMemo(() => {
    const item = activeTerminal()
    const base = query()
    if (!item || !base) return undefined
    return `${base.url}\n${item.directory}\n${item.id}`
  })
  const terminalTarget = createMemo<Query | undefined>(() => {
    const item = activeTerminal()
    const base = query()
    if (!item || !base) return undefined
    return { url: base.url, dir: item.directory, scope: "project" }
  })
  const [detail] = createResource(detailKey, (item) => loadProjectDiffFile(item.input, item.dir, item.file))
  const settings = createMemo(() => {
    const q = search().toString()
    return `/projects/${encodeURIComponent(project())}/settings${q ? `?${q}` : ""}`
  })

  function terminalsFor(dir: string) {
    return grouped().get(dir) ?? []
  }

  function remember(dir: string, pty?: string) {
    window.localStorage.setItem(`kilo.console.${project()}.dir`, dir)
    if (pty) {
      window.localStorage.setItem(`kilo.console.${project()}.pty`, pty)
      return
    }
    window.localStorage.removeItem(`kilo.console.${project()}.pty`)
  }

  function select(item: Context) {
    setSelected(item.dir)
    const pty = terminalsFor(item.dir)[0]
    setActive(pty?.id ?? "")
    setFile(undefined)
    remember(item.dir, pty?.id)
  }

  function selectTerminal(item: ProjectTerminalItem) {
    setSelected(item.directory)
    setActive(item.id)
    setFile(undefined)
    remember(item.directory, item.id)
  }

  function run(label: string, job: () => Promise<unknown>) {
    setSaving(label)
    setFailure(undefined)
    void job()
      .then(() => refetch())
      .catch((err) => setFailure(errMsg(err)))
      .finally(() => setSaving(undefined))
  }

  function addWorktree() {
    const input = target()
    const data = snap()
    if (!input || !data) return
    const name = window.prompt("Worktree name") ?? undefined
    run("Creating worktree", async () => {
      const next = await createProjectWorktree({ ...input, dir: data.project.worktree }, name)
      setSelected(next.directory)
      window.localStorage.setItem(`kilo.console.${project()}.dir`, next.directory)
    })
  }

  function addSession() {
    const input = target()
    const item = current()
    if (!input || !item) return
    const label = `Kilo ${terminalsFor(item.dir).length + 1}`
    setSaving("Creating session")
    setFailure(undefined)
    void createProjectPty(input, item.dir, label)
      .then((pty) => {
        const next = { ...pty, directory: item.dir }
        setLocal((rows) => [...rows.filter((row) => row.id !== next.id), next])
        setSelected(item.dir)
        setActive(next.id)
        remember(item.dir, next.id)
        return refetch()
      })
      .catch((err) => setFailure(errMsg(err)))
      .finally(() => setSaving(undefined))
  }

  function dropTerminal(id: string) {
    setLocal((rows) => rows.filter((row) => row.id !== id))
    if (active() === id) {
      setActive("")
      window.localStorage.removeItem(`kilo.console.${project()}.pty`)
    }
    void refetch()
  }

  function removeSelected() {
    const input = target()
    const item = current()
    const data = snap()
    if (!input || !item || !data || item.kind === "local") return
    if (!window.confirm(`Remove worktree ${item.label}?`)) return
    run("Removing worktree", async () => {
      await removeProjectWorktree({ ...input, dir: data.project.worktree }, item.dir)
      setSelected(data.project.worktree)
    })
  }

  function resetSelected() {
    const input = target()
    const item = current()
    const data = snap()
    if (!input || !item || !data || item.kind === "local") return
    if (!window.confirm(`Reset worktree ${item.label}?`)) return
    run("Resetting worktree", async () => resetProjectWorktree({ ...input, dir: data.project.worktree }, item.dir))
  }

  createEffect(() => {
    const next = search().get("server")
    if (next && next !== url()) setUrl(next)
  })

  createEffect(() => {
    if (!discoverable(search())) return
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
    const data = snap()
    if (!data) return
    const hit = contexts().some((item) => item.dir === selected())
    if (hit) return
    setSelected(data.project.worktree)
    remember(data.project.worktree)
  })

  createEffect(() => {
    const ids = new Set((snap()?.terminals ?? []).map((item) => item.id))
    if (ids.size === 0) return
    const rows = local()
    if (!rows.some((item) => ids.has(item.id))) return
    setLocal(rows.filter((item) => !ids.has(item.id)))
  })

  createEffect(() => {
    const item = current()
    if (!item) return
    const hit = activeTerminal()
    if (hit?.directory === item.dir) return
    const pty = terminalsFor(item.dir)[0]
    if ((pty?.id ?? "") === active()) return
    setActive(pty?.id ?? "")
    remember(item.dir, pty?.id)
  })

  createEffect(() => {
    if (!snap.error || !discoverable(search())) return
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
    <section class="project-console">
      <aside class="project-console-sidebar" aria-label="Project console sections">
        <div class="project-console-title">
          <span>Project</span>
          <span class="project-console-scope">
            <span class="project-console-dot" aria-hidden="true" />
            <span>
              {snap()?.project.name ? friendly(snap()?.project.name ?? "") : title(snap()?.project.worktree ?? project())}
            </span>
          </span>
          <small>{snap()?.vcs.branch ?? "No branch"}</small>
        </div>
        <div class="project-console-scroll">
          <section class="project-sidebar-group">
            <div class="project-panel-heading">Actions</div>
            <div class="project-console-actions">
              <button type="button" onClick={addWorktree} disabled={!target() || !!saving()}>
                New worktree
              </button>
              <button type="button" onClick={addSession} disabled={!target() || !!saving()}>
                New session
              </button>
            </div>
          </section>
          <section class="project-sidebar-group">
            <div class="project-panel-heading">Worktrees</div>
            <nav class="project-contexts" aria-label="Worktrees">
              <For each={contexts()}>
                {(item) => (
                  <div class="project-worktree-block">
                    <button
                      type="button"
                      class="project-context"
                      classList={{ active: current()?.dir === item.dir && !activeTerminal() }}
                      onClick={() => select(item)}
                      title={item.dir}
                    >
                      <span>{item.label}</span>
                      <small>{item.kind === "local" ? "project" : repo(item.dir)}</small>
                    </button>
                    <Show when={terminalsFor(item.dir).length > 0}>
                      <div class="project-terminal-list">
                        <For each={terminalsFor(item.dir)}>
                          {(pty) => (
                            <button
                              type="button"
                              class="project-terminal-row"
                              classList={{ active: active() === pty.id }}
                              onClick={() => selectTerminal(pty)}
                              title={`${pty.title} (${pty.pid})`}
                            >
                              <span>{pty.title || `Terminal ${pty.id.slice(-4)}`}</span>
                            </button>
                          )}
                        </For>
                      </div>
                    </Show>
                  </div>
                )}
              </For>
            </nav>
          </section>
        </div>
        <div class="project-sidebar-bottom">
          <A class="project-settings-link" href={settings()}>
            <span>Settings</span>
            <small>Project configuration</small>
          </A>
        </div>
      </aside>

      <main class="project-console-main">
        <Show when={!query() && discoverable(search())}>
          <Card class="banner" variant="info">
            Discovering Kilo server...
          </Card>
        </Show>
        <Show when={snap.loading && !snap()}>
          <Card class="banner" variant="info">
            Loading project console...
          </Card>
        </Show>
        <Show when={snap.error || failure()}>
          <Card class="banner" variant="error">
            <strong>Project console failed</strong>
            <span>{failure() ?? errMsg(snap.error)}</span>
          </Card>
        </Show>
        <Show keyed when={terminal()}>
          {(_) => {
            const item = terminalTarget()
            const pty = activeTerminal()
            if (!item || !pty) return null
            return <GhosttyTerminal query={item} pty={pty.id} onExit={() => dropTerminal(pty.id)} />
          }}
        </Show>
        <Show when={!terminal() && !snap.loading && !snap.error && !failure()}>
          <div class="project-terminal-empty">
            <strong>No terminal session selected</strong>
            <span>Use New session to start Kilo CLI in this worktree.</span>
          </div>
        </Show>
      </main>

      <aside class="project-console-info" aria-label="Project details">
        <div class="project-info-card">
          <div class="project-panel-heading">Context</div>
          <strong>{current()?.label ?? "Project"}</strong>
          <code>{current()?.dir ?? snap()?.project.worktree ?? project()}</code>
          <Show when={current()?.kind === "worktree"}>
            <div class="project-info-actions">
              <button type="button" onClick={resetSelected} disabled={!!saving()}>
                Reset
              </button>
              <button type="button" onClick={removeSelected} disabled={!!saving()}>
                Remove
              </button>
            </div>
          </Show>
        </div>
        <div class="project-info-card grow">
          <div class="project-panel-heading">Changes</div>
          <Show when={diffs.loading}>
            <p class="empty">Loading diff...</p>
          </Show>
          <Show when={diffs.error}>
            <p class="empty">{errMsg(diffs.error)}</p>
          </Show>
          <Show when={!diffs.loading && (diffs() ?? []).length === 0 && !diffs.error}>
            <p class="empty">No changes detected.</p>
          </Show>
          <div class="project-diff-list">
            <For each={diffs() ?? []}>
              {(item) => (
                <button
                  type="button"
                  class="project-diff-row"
                  classList={{ active: file() === item.file }}
                  onClick={() => setFile(item.file)}
                >
                  <span>{item.file}</span>
                  <small>
                    +{item.additions} -{item.deletions}
                  </small>
                </button>
              )}
            </For>
          </div>
          <Show when={detail()}>
            {(item) => <pre class="project-diff-detail">{item()?.patch ?? ""}</pre>}
          </Show>
        </div>
      </aside>
    </section>
  )
}
