import { IconButton } from "@kilocode/kilo-web-ui/icon-button"

export function AppHeader() {
  return (
    <header class="app-header">
      <a class="header-brand" href="/projects" aria-label="Kilo Console home">
        <span class="header-mark" aria-hidden="true">
          K
        </span>
        <span class="header-title">
          <strong>Kilo</strong>
          <span>Console</span>
        </span>
      </a>

      <form class="omni-search" role="search">
        <span aria-hidden="true">⌘K</span>
        <input type="search" placeholder="Search projects, config, providers..." aria-label="Omni search" />
      </form>

      <nav class="notification-zone" aria-label="Notifications and status">
        <IconButton icon="bubble-5" variant="ghost" aria-label="Notifications" />
        <IconButton icon="help" variant="ghost" aria-label="Help" />
        <IconButton icon="circle-check" variant="ghost" aria-label="System status" />
      </nav>
    </header>
  )
}
