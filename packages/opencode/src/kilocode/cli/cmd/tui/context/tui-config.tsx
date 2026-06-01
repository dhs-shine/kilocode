// kilocode_change - new file
/**
 * Reactive TUI config provider with hot reload.
 *
 * Replaces the static upstream `TuiConfigProvider` so that keybinds, theme, and other
 * declarative TUI settings apply live when changed from the Kilo Console — no restart.
 *
 * The reload wiring (subscribe + refetch) lives in `tui-config-hot-reload.ts` so this module
 * stays free of SDK/event imports and the store factory can be unit-tested in isolation.
 */
import { createContext, useContext, type ParentProps } from "solid-js"
import { createStore, reconcile } from "solid-js/store"
import type { TuiConfig } from "@/cli/cmd/tui/config/tui"

export type SetTuiConfig = (next: TuiConfig.Info) => void

const ConfigContext = createContext<TuiConfig.Info>()
const SetContext = createContext<SetTuiConfig>()

export namespace KiloTuiConfig {
  // Pure factory so the reactive behavior is unit-testable without JSX/contexts.
  export function makeStore(initial: TuiConfig.Info) {
    const [store, setStore] = createStore<TuiConfig.Info>(initial)
    // The fetched config is the server's effective TUI config and replaces the store. We only
    // hot-reload declarative settings (keybinds/theme/ui); the plugin runtime is initialized
    // once at startup and is unaffected by the store losing `plugin_origins` here. `merge: true`
    // reconciles arrays by index instead of key-diffing (TUI config arrays have no `id`).
    const set: SetTuiConfig = (next) => setStore(reconcile(next, { merge: true }))
    return { config: store, set }
  }

  export function Provider(props: ParentProps<{ config: TuiConfig.Info }>) {
    const store = makeStore(props.config)
    return (
      <ConfigContext.Provider value={store.config}>
        <SetContext.Provider value={store.set}>{props.children}</SetContext.Provider>
      </ConfigContext.Provider>
    )
  }

  export function use() {
    const value = useContext(ConfigContext)
    if (!value) throw new Error("TuiConfig context must be used within a context provider")
    return value
  }

  export function useSet() {
    const value = useContext(SetContext)
    if (!value) throw new Error("TuiConfig context must be used within a context provider")
    return value
  }
}
