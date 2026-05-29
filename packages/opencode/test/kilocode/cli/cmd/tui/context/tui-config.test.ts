/**
 * Locks in the reactive TUI config store used for hot reload. `useKeybind`/`useTheme` read the
 * store proxy reactively, so `set()` (driven by `global.config.updated`) must propagate new
 * keybinds/theme to tracked reads — otherwise the TUI would still require a restart.
 */
import { describe, expect, test } from "bun:test"
import { createEffect, createRoot } from "solid-js"
import type { TuiConfig } from "@/cli/cmd/tui/config/tui"
import { KiloTuiConfig } from "@/kilocode/cli/cmd/tui/context/tui-config"

function cfg(input: Partial<TuiConfig.Info>): TuiConfig.Info {
  return input as TuiConfig.Info
}

describe("KiloTuiConfig.makeStore", () => {
  test("reactive reads update when set() reconciles a new config", () => {
    const store = KiloTuiConfig.makeStore(cfg({ keybinds: { app_exit: "ctrl+c" }, theme: "kilo" }))

    const exits: Array<string | undefined> = []
    const themes: Array<string | undefined> = []
    let dispose!: () => void
    createRoot((d) => {
      dispose = d
      createEffect(() => exits.push(store.config.keybinds?.app_exit))
      createEffect(() => themes.push(store.config.theme))
    })

    // Initial tracked reads.
    expect(exits).toEqual(["ctrl+c"])
    expect(themes).toEqual(["kilo"])

    store.set(cfg({ keybinds: { app_exit: "ctrl+q", leader: "ctrl+x" }, theme: "nord" }))

    // Direct store reads reflect the update synchronously.
    expect(store.config.keybinds?.app_exit).toBe("ctrl+q")
    expect(store.config.keybinds?.leader).toBe("ctrl+x")
    expect(store.config.theme).toBe("nord")

    // Tracked reactive reads re-ran with the new values (the hot-reload contract).
    expect(exits).toEqual(["ctrl+c", "ctrl+q"])
    expect(themes).toEqual(["kilo", "nord"])

    dispose()
  })

  test("set() does not re-notify a tracked read when its value is unchanged", () => {
    const store = KiloTuiConfig.makeStore(cfg({ keybinds: { app_exit: "ctrl+c" }, theme: "kilo" }))

    const exits: Array<string | undefined> = []
    let dispose!: () => void
    createRoot((d) => {
      dispose = d
      createEffect(() => exits.push(store.config.keybinds?.app_exit))
    })

    // Only the theme changes; the tracked keybind stays "ctrl+c".
    store.set(cfg({ keybinds: { app_exit: "ctrl+c" }, theme: "nord" }))

    expect(store.config.theme).toBe("nord")
    expect(exits).toEqual(["ctrl+c"])

    dispose()
  })
})
