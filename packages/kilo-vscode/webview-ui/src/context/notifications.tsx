import {
  createContext,
  useContext,
  createSignal,
  createMemo,
  onMount,
  onCleanup,
  ParentComponent,
  Accessor,
} from "solid-js"
import { useVSCode } from "./vscode"
import type { KilocodeNotification, ExtensionMessage } from "../types/messages"

// Static notifications always shown unconditionally (not fetched from API, not dismissable)
const STATIC_NOTIFICATIONS: KilocodeNotification[] = [
  {
    id: "star-giveaway-june-2026",
    title: "GitHub Star Giveaway",
    message: "GitHub Star Giveaway: $500 to 2 people who star us by June 24th",
    action: { actionText: "Star us on GitHub", actionURL: "https://github.com/Kilo-Org/kilocode/" },
  },
]

const STATIC_IDS = new Set(STATIC_NOTIFICATIONS.map((n) => n.id))

interface NotificationsContextValue {
  notifications: Accessor<KilocodeNotification[]>
  filteredNotifications: Accessor<KilocodeNotification[]>
  dismiss: (id: string) => void
}

export const NotificationsContext = createContext<NotificationsContextValue>()

export const NotificationsProvider: ParentComponent = (props) => {
  const vscode = useVSCode()
  const [notifications, setNotifications] = createSignal<KilocodeNotification[]>([])
  const [dismissedIds, setDismissedIds] = createSignal<string[]>([])

  const unsubscribe = vscode.onMessage((message: ExtensionMessage) => {
    if (message.type === "notificationsLoaded") {
      setNotifications(message.notifications)
      setDismissedIds(message.dismissedIds)
    }
  })

  onMount(() => {
    let retries = 0
    const request = () => {
      vscode.postMessage({ type: "requestNotifications" })
    }
    request()
    const interval = setInterval(() => {
      if (notifications().length > 0 || retries >= 5) {
        clearInterval(interval)
        return
      }
      retries++
      request()
    }, 500)
    onCleanup(() => {
      clearInterval(interval)
      unsubscribe()
    })
  })

  const filteredNotifications = createMemo(() => {
    const dismissed = dismissedIds()
    const api = notifications().filter((n) => !dismissed.includes(n.id))
    return [...STATIC_NOTIFICATIONS, ...api]
  })

  const dismiss = (id: string) => {
    // Static notifications are always shown and cannot be dismissed
    if (STATIC_IDS.has(id)) return
    setDismissedIds((prev) => (prev.includes(id) ? prev : [...prev, id]))
    vscode.postMessage({ type: "dismissNotification", notificationId: id })
  }

  const value: NotificationsContextValue = {
    notifications,
    filteredNotifications,
    dismiss,
  }

  return <NotificationsContext.Provider value={value}>{props.children}</NotificationsContext.Provider>
}

export function useNotifications(): NotificationsContextValue {
  const context = useContext(NotificationsContext)
  if (!context) {
    throw new Error("useNotifications must be used within a NotificationsProvider")
  }
  return context
}
