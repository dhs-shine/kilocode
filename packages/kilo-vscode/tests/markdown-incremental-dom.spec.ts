import { expect, test } from "@playwright/test"
import { build } from "esbuild"
import { fileURLToPath } from "node:url"

const source = fileURLToPath(new URL("../../ui/src/kilocode/markdown-incremental-dom.ts", import.meta.url))
const bundle = await build({
  stdin: {
    contents: `
      import { createIncrementalMarkdown } from ${JSON.stringify(source)}
      globalThis.createIncrementalMarkdown = createIncrementalMarkdown
    `,
    resolveDir: fileURLToPath(new URL(".", import.meta.url)),
  },
  bundle: true,
  format: "iife",
  platform: "browser",
  write: false,
})
const script = bundle.outputFiles[0]!.text

test.beforeEach(async ({ page }) => {
  await page.goto("about:blank")
  await page.addScriptTag({ content: script })
})

test("keeps completed Markdown nodes while replacing only the live tail", async ({ page }) => {
  const result = await page.evaluate(() => {
    const create = (
      globalThis as typeof globalThis & {
        createIncrementalMarkdown: (decorate: () => void) => {
          update: (
            container: HTMLDivElement,
            blocks: Array<{ key: string; hash: string; html: string; mode: "full" | "live" }>,
            labels: { copy: string; copied: string },
          ) => boolean
        }
      }
    ).createIncrementalMarkdown
    const labels = { copy: "Copy", copied: "Copied" }
    const container = document.createElement("div")
    document.body.append(container)
    const renderer = create(() => {})
    const heading = { key: "0", hash: "heading", html: "<h2>Heading</h2>", mode: "full" as const }
    const tail = { key: "1", hash: "tail-a", html: "<p>Tail A</p>", mode: "live" as const }

    renderer.update(container, [heading, tail], labels)
    const stable = container.children[0]
    const previous = container.children[1]
    renderer.update(container, [heading, { ...tail, hash: "tail-b", html: "<p>Tail B</p>" }], labels)

    return {
      stable: container.children[0] === stable,
      replaced: container.children[1] !== previous,
      tags: Array.from(container.children).map((child) => child.tagName),
      text: container.textContent,
    }
  })

  expect(result).toEqual({ stable: true, replaced: true, tags: ["H2", "P"], text: "HeadingTail B" })
})

test("rebuilds safely when a Markdown boundary disappears", async ({ page }) => {
  const result = await page.evaluate(() => {
    const create = (
      globalThis as typeof globalThis & {
        createIncrementalMarkdown: (decorate: () => void) => {
          update: (
            container: HTMLDivElement,
            blocks: Array<{ key: string; hash: string; html: string; mode: "full" | "live" }>,
            labels: { copy: string; copied: string },
          ) => boolean
        }
      }
    ).createIncrementalMarkdown
    const labels = { copy: "Copy", copied: "Copied" }
    const container = document.createElement("div")
    document.body.append(container)
    const renderer = create(() => {})
    const blocks = [
      { key: "0", hash: "stable", html: "<p>Stable</p>", mode: "full" as const },
      { key: "1", hash: "middle", html: "<p>Middle</p>", mode: "full" as const },
      { key: "2", hash: "tail", html: "<p>Tail</p>", mode: "live" as const },
    ]

    renderer.update(container, blocks, labels)
    const comments = Array.from(container.childNodes).filter((node) => node.nodeType === Node.COMMENT_NODE)
    comments.at(-1)?.remove()
    const updated = renderer.update(container, [blocks[0]!, { ...blocks[1]!, mode: "live" }], labels)

    return {
      updated,
      tags: Array.from(container.children).map((child) => child.tagName),
      text: container.textContent,
    }
  })

  expect(result).toEqual({ updated: true, tags: ["P", "P"], text: "StableMiddle" })
})
