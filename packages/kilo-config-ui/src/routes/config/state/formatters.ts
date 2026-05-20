import { createMemo, createSignal } from "solid-js"
import { useConfig } from "../../../context/config"
import { clean, csv, words } from "../../../shared/utils"

export function useFormatterSettings() {
  const ctx = useConfig()
  const snap = () => ctx.data()
  const [fmt, setFmt] = createSignal("")
  const [fmtCommand, setFmtCommand] = createSignal("")
  const [fmtExtensions, setFmtExtensions] = createSignal("")
  const [lsp, setLsp] = createSignal("")
  const [lspCommand, setLspCommand] = createSignal("")
  const [lspExtensions, setLspExtensions] = createSignal("")
  const formatters = createMemo(() => snap()?.overlay.collections.formatter ?? [])
  const lsps = createMemo(() => snap()?.overlay.collections.lsp ?? [])

  function addFormatter() {
    const data = snap()
    const name = clean(fmt())
    const command = words(fmtCommand())
    if (!data || !name || command.length === 0) {
      ctx.fail("Enter a formatter name and command before saving.")
      return
    }
    ctx.save({ formatter: { [name]: { command, extensions: csv(fmtExtensions()) } } })
  }

  function addLsp() {
    const data = snap()
    const name = clean(lsp())
    const command = words(lspCommand())
    if (!data || !name || command.length === 0) {
      ctx.fail("Enter an LSP name and command before saving.")
      return
    }
    ctx.save({ lsp: { [name]: { command, extensions: csv(lspExtensions()) } } })
  }

  return {
    ctx,
    snap,
    fmt,
    setFmt,
    fmtCommand,
    setFmtCommand,
    fmtExtensions,
    setFmtExtensions,
    lsp,
    setLsp,
    lspCommand,
    setLspCommand,
    lspExtensions,
    setLspExtensions,
    formatters,
    lsps,
    addFormatter,
    addLsp,
  }
}
