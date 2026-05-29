// Kilo-specific replacement for the shared `@opencode-ai/ui/pierre/worker`
// module (wired up by the pierre-worker-alias plugin in esbuild.js).
//
// The upstream module loads Pierre's Shiki worker through a Vite-only
// `?worker&url` import, which esbuild cannot resolve and which the VS Code
// webview CSP blocks. As a result the webview previously ran syntax
// highlighting synchronously on the main thread (`findNextMatchSync`), which
// froze scrolling on large diffs.
//
// Here we instead load the worker from a real dist asset (`dist/shiki-worker.js`,
// also produced by esbuild.js) using the webview URI the extension injects into
// the page. Highlighting then runs off the main thread and scrolling stays
// smooth. If the URI is missing (or the worker fails to spawn) Pierre falls back
// to its main-thread highlighter, so behaviour is never worse than before.
import { WorkerPoolManager } from "@pierre/diffs/worker"

export type WorkerPoolStyle = "unified" | "split"

// Oniguruma WebAssembly engine (matches the highlighting quality of the rest of
// the app). The CSP already allows `wasm-unsafe-eval`, and if the worker fails to
// instantiate, WorkerPoolManager falls back to the main-thread highlighter. To
// avoid WebAssembly entirely, build the portable worker and use "shiki-js".
const ENGINE = "shiki-wasm"

function uri(): string | undefined {
  if (typeof window === "undefined") return undefined
  return (window as { KILO_SHIKI_WORKER_URI?: string }).KILO_SHIKI_WORKER_URI
}

export function workerFactory(): Worker {
  const url = uri()
  if (!url) throw new Error("KILO_SHIKI_WORKER_URI is not set")
  return new Worker(url)
}

function createPool(lineDiffType: "none" | "word-alt") {
  const pool = new WorkerPoolManager(
    { workerFactory, poolSize: 2 },
    { theme: "Kilo", lineDiffType, preferredHighlighter: ENGINE },
  )
  void pool.initialize()
  return pool
}

let unified: WorkerPoolManager | undefined
let split: WorkerPoolManager | undefined

export function getWorkerPool(style: WorkerPoolStyle | undefined): WorkerPoolManager | undefined {
  // No injected worker URI means we can't spawn the worker; returning undefined
  // makes Pierre fall back to the existing main-thread highlighter.
  if (!uri()) return undefined

  if (style === "split") {
    if (!split) split = createPool("word-alt")
    return split
  }

  if (!unified) unified = createPool("none")
  return unified
}

export function getWorkerPools() {
  return {
    unified: getWorkerPool("unified"),
    split: getWorkerPool("split"),
  }
}
