import { A, useLocation } from "@solidjs/router"
import { createMemo, createSignal, For } from "solid-js"
import { Icon } from "@kilocode/kilo-ui/icon"
import { configNav, type ConfigGroup, type ConfigNode } from "./sections"

export function ConfigSidebar() {
  const loc = useLocation()
  const [open, setOpen] = createSignal<Record<string, boolean>>({})
  const base = createMemo(() => {
    const index = loc.pathname.indexOf("/settings")
    if (index > 0) return `${loc.pathname.slice(0, index)}/settings`
    return "/settings"
  })
  const active = createMemo(() => {
    const rest = loc.pathname.slice(base().length)
    if (rest === "/models") return "/models/default"
    return rest || "/"
  })
  const href = (path: string) => `${path === "/" ? base() : `${base()}${path}`}${loc.search}`
  const current = (path: string) => path === active() || (path !== "/" && active().startsWith(`${path}/`))
  const group = (item: ConfigNode): item is ConfigGroup => "items" in item
  const marked = (group: ConfigGroup) => group.items.some((item) => current(item.path))
  const expanded = (group: ConfigGroup) => marked(group) || Boolean(open()[group.id])
  const toggle = (group: ConfigGroup) => setOpen((value) => ({ ...value, [group.id]: !expanded(group) }))

  return (
    <aside class="config-sidebar" aria-label="Configuration sections">
      <div class="config-sidebar-title">Settings</div>
      <nav class="config-options">
        <For each={configNav}>
          {(item) => {
            if (!group(item)) {
              return (
                <A class="config-top-option" classList={{ active: current(item.path) }} href={href(item.path)}>
                  <Icon name={item.icon} size="small" />
                  <span>{item.label}</span>
                </A>
              )
            }

            return (
              <section class="config-group">
                <button
                  class="config-group-title"
                  type="button"
                  aria-expanded={expanded(item)}
                  aria-controls={`config-group-${item.id}`}
                  onClick={() => toggle(item)}
                >
                  <Icon name={expanded(item) ? "chevron-down" : "chevron-right"} size="small" />
                  <span>{item.label}</span>
                </button>
                <div id={`config-group-${item.id}`} class="config-group-items" hidden={!expanded(item)}>
                  <For each={item.items}>
                    {(child) => (
                      <A class="config-option" classList={{ active: current(child.path) }} href={href(child.path)}>
                        <Icon name={child.icon} size="small" />
                        <span>{child.label}</span>
                      </A>
                    )}
                  </For>
                </div>
              </section>
            )
          }}
        </For>
      </nav>
    </aside>
  )
}
