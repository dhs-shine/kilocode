import type { Event, GlobalEvent } from "@kilocode/sdk/v2"

type SyncEvent = Extract<GlobalEvent["payload"], { type: "sync" }>
import { useProject } from "./project"
import { useSDK } from "./sdk"

type EventMetadata = {
  workspace: string | undefined
}

export function useEvent() {
  const project = useProject()
  const sdk = useSDK()

  function subscribe(handler: (event: Event, metadata: EventMetadata) => void) {
    return sdk.event.on("event", (event) => {
      if (event.payload.type === "sync") return
      if (event.directory === "global" || event.project === project.project()) {
        handler(event.payload, { workspace: event.workspace })
      }
    })
  }

  function sync(handler: (event: SyncEvent, metadata: EventMetadata) => void) {
    return sdk.event.on("event", (event) => {
      if (event.payload.type !== "sync") return
      if (event.directory === "global" || event.project === project.project()) {
        handler(event.payload, { workspace: event.workspace })
      }
    })
  }

  function on<T extends Event["type"]>(
    type: T,
    handler: (event: Extract<Event, { type: T }>, metadata: EventMetadata) => void,
  ) {
    return subscribe((event: Event, metadata: EventMetadata) => {
      if (event.type !== type) return
      handler(event as Extract<Event, { type: T }>, metadata)
    })
  }

  function onSync<T extends SyncEvent["name"]>(
    name: T,
    handler: (event: Extract<SyncEvent, { name: T }>, metadata: EventMetadata) => void,
  ) {
    return sync((event: SyncEvent, metadata: EventMetadata) => {
      if (event.name !== name) return
      handler(event as Extract<SyncEvent, { name: T }>, metadata)
    })
  }

  return {
    subscribe,
    sync,
    on,
    onSync,
  }
}
