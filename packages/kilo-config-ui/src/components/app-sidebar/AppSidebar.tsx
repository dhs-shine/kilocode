import { A, useLocation } from "@solidjs/router"
import { For } from "solid-js"
import { IconButton } from "@kilocode/kilo-ui/icon-button"
import { projects, type Path } from "../../shared/navigation"

type Props = {
  path: Path
}

export function AppSidebar(props: Props) {
  const loc = useLocation()
  const search = () => {
    const params = new URLSearchParams(loc.search)
    params.delete("directory")
    const query = params.toString()
    return query ? `?${query}` : ""
  }
  const settings = () => {
    const suffix = search()
    return `/settings${suffix}`
  }

  return (
    <aside class="rail" aria-label="Primary navigation">
      <A class="rail-action" classList={{ active: props.path === "/projects" }} href="/projects" aria-label="Projects">
        <IconButton icon="folder" variant="ghost" tabindex={-1} />
      </A>

      <div class="rail-favorites" aria-label="Favorite projects">
        <For each={projects}>
          {(item) => (
            <A class="favorite-project" href={item.path} aria-label={item.name} title={item.name}>
              {item.label}
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
