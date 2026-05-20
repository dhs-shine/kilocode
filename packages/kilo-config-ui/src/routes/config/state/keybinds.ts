import { createMemo, createSignal } from "solid-js"
import type { TuiPatch } from "../../../client"
import { useConfig } from "../../../context/config"
import { clean, dupBindings } from "../../../shared/utils"

export function useKeybindSettings() {
  const ctx = useConfig()
  const snap = () => ctx.data()
  const [key, setKey] = createSignal("leader")
  const [binding, setBinding] = createSignal("")
  const keybinds = createMemo(() => Object.entries(snap()?.tui.keybinds ?? {}).slice(0, 24))
  const conflicts = createMemo(() => {
    const data = snap()
    if (!data) return []
    return dupBindings(data, key(), binding())
  })

  function save() {
    const data = snap()
    const name = clean(key())
    const value = clean(binding())
    if (!data || !name || !value) {
      ctx.fail("Enter a TUI keybind name and binding before saving.")
      return
    }
    const next: NonNullable<TuiPatch["keybinds"]> = { ...data.tui.keybinds }
    Object.assign(next, { [name]: value })
    ctx.tui({ keybinds: next })
  }

  return { ctx, key, setKey, binding, setBinding, keybinds, conflicts, save }
}
