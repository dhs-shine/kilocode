import { createEffect, createMemo, createSignal } from "solid-js"
import { useConfig } from "../../../context/config"
import { clean } from "../../../shared/utils"

export function useTuiUiSettings() {
  const ctx = useConfig()
  const current = createMemo(() => ctx.data()?.tui.theme ?? "")
  const [theme, setTheme] = createSignal("")
  const [dirty, setDirty] = createSignal(false)

  createEffect(() => {
    if (dirty()) return
    setTheme(current())
  })

  function update(value: string) {
    setDirty(true)
    setTheme(value)
  }

  function save() {
    const value = clean(theme())
    if (!value) {
      ctx.fail("Enter a TUI theme before saving.")
      return
    }
    ctx.tui({ theme: value })
    setDirty(false)
  }

  return { ctx, current, theme, setTheme: update, save }
}
