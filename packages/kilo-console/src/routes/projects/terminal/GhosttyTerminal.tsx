import { createEffect, createSignal, onCleanup, onMount, Show } from "solid-js"
import { FitAddon, init, Terminal } from "ghostty-web"
import { ptyWsUrl, resizeProjectPty, type Query } from "../../../client"

let boot: Promise<void> | undefined

function ready() {
  boot ??= init()
  return boot
}

function css(el: Element, name: string, fallback: string) {
  const value = getComputedStyle(el).getPropertyValue(name).trim()
  return value || fallback
}

function px(el: Element, name: string, fallback: number) {
  const value = css(el, name, "")
  const size = Number.parseFloat(value)
  if (!Number.isFinite(size) || size <= 0) return fallback
  if (value.endsWith("rem") || value.endsWith("em")) {
    const root = Number.parseFloat(getComputedStyle(document.documentElement).fontSize)
    return Number.isFinite(root) ? size * root : fallback
  }
  return size
}

function theme(el: Element) {
  const background = css(el, "--project-terminal-background", "#0f1015")
  return {
    background,
    foreground: css(el, "--foreground", "#d4d4d8"),
    cursor: css(el, "--foreground", "#d4d4d8"),
    selectionBackground: css(el, "--accent", "#334155"),
    black: "#0f1015",
    red: "#f87171",
    green: "#34d399",
    yellow: "#fbbf24",
    blue: "#60a5fa",
    magenta: "#c084fc",
    cyan: "#22d3ee",
    white: "#e5e7eb",
    brightBlack: "#71717a",
    brightRed: "#fca5a5",
    brightGreen: "#86efac",
    brightYellow: "#fde68a",
    brightBlue: "#93c5fd",
    brightMagenta: "#d8b4fe",
    brightCyan: "#67e8f9",
    brightWhite: "#fafafa",
  }
}

export function GhosttyTerminal(props: { query: Query; pty: string; active?: boolean; onExit?: () => void }) {
  let host!: HTMLDivElement
  let term: Terminal | undefined
  let fit: FitAddon | undefined
  const [failure, setFailure] = createSignal<string | undefined>()
  const [shown, setShown] = createSignal(false)

  createEffect(() => {
    if (!props.active || !shown()) return
    requestAnimationFrame(() => {
      fit?.fit()
      term?.focus()
    })
  })

  onMount(() => {
    let disposed = false
    let ws: WebSocket | undefined
    let data: { dispose: () => void } | undefined
    let size: { dispose: () => void } | undefined
    let replay = false

    const done = (input: Uint8Array) => {
      if (input[0] !== 0) return false
      replay = true
      requestAnimationFrame(() => {
        if (!disposed) setShown(true)
      })
      return true
    }

    const run = async () => {
      host.replaceChildren()
      await ready()
      if (disposed) return

      term = new Terminal({
        cols: 100,
        rows: 30,
        cursorBlink: true,
        fontFamily: "'FiraCode Nerd Font', 'FiraCode Nerd Font Mono', 'Fira Code', Menlo, Monaco, 'Courier New', monospace",
        fontSize: Math.max(14, px(host, "--font-size-base", 14)),
        scrollback: 5000,
        theme: theme(host),
      })
      fit = new FitAddon()
      term.loadAddon(fit)
      host.replaceChildren()
      term.open(host)
      term.reset()
      term.clear()
      data = term.onData((input) => {
        if (replay && ws?.readyState === WebSocket.OPEN) ws.send(input)
      })
      size = term.onResize((next) => {
        void resizeProjectPty(props.query, props.pty, next.cols, next.rows).catch((err) =>
          console.warn("Terminal resize failed", err),
        )
      })
      fit.fit()
      fit.observeResize()
      requestAnimationFrame(() => {
        fit?.fit()
        if (props.active) term?.focus()
      })

      ws = new WebSocket(ptyWsUrl(props.query, props.pty))
      ws.binaryType = "arraybuffer"
      ws.onmessage = (event) => {
        if (disposed || !term) return
        if (typeof event.data === "string") {
          term.write(event.data)
          return
        }
        if (event.data instanceof ArrayBuffer) {
          const bytes = new Uint8Array(event.data)
          if (done(bytes)) return
          term.write(bytes)
          return
        }
        if (event.data instanceof Blob) {
          void event.data.arrayBuffer().then((buffer) => {
            if (disposed || !term) return
            const bytes = new Uint8Array(buffer)
            if (done(bytes)) return
            term.write(bytes)
          })
        }
      }
      ws.onerror = () => setFailure("Terminal WebSocket connection failed")
      ws.onclose = () => {
        if (disposed) return
        setFailure("Terminal disconnected")
        props.onExit?.()
      }
    }

    void run().catch((err) => {
      const msg = err instanceof Error ? err.message : String(err)
      setFailure(msg)
    })

    onCleanup(() => {
      disposed = true
      data?.dispose()
      size?.dispose()
      fit?.dispose()
      ws?.close()
      term?.dispose()
      host.replaceChildren()
      fit = undefined
      term = undefined
    })
  })

  return (
    <div class="project-terminal" classList={{ shown: shown() }}>
      <div ref={host} class="project-terminal-host" />
      <Show when={failure()}>
        {(msg) => <div class="project-terminal-error">{msg()}</div>}
      </Show>
    </div>
  )
}
