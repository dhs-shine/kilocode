import { IconButton } from "@kilocode/kilo-ui/icon-button"
import { Mark } from "@kilocode/kilo-ui/logo"

export function AppHeader() {
  return (
    <header class="app-header">
      <a class="header-brand" href="/projects" aria-label="Kilo Console home">
        <span class="header-mark">
          <Mark />
        </span>
        <strong>Kilo Console</strong>
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
