import { afterEach, beforeEach, describe, expect, test } from "bun:test"
import { Window } from "happy-dom"
import { createIncrementalMarkdown, type MarkdownBlock } from "./markdown-incremental-dom"

const labels = { copy: "Copy", copied: "Copied" }

function block(key: string, hash: string, html: string, mode: "full" | "live" = "full"): MarkdownBlock {
  return { key, hash, html, mode }
}

describe("incremental markdown DOM", () => {
  let window: Window

  beforeEach(() => {
    window = new Window()
    Object.defineProperty(globalThis, "document", { configurable: true, value: window.document })
  })

  afterEach(() => {
    window.close()
    Reflect.deleteProperty(globalThis, "document")
  })

  test("keeps completed block nodes while replacing only the live tail", () => {
    const container = document.createElement("div")
    document.body.append(container)
    const renderer = createIncrementalMarkdown(() => {})
    const initial = [block("0", "heading", "<h2>Heading</h2>"), block("1", "tail-a", "<p>Tail A</p>", "live")]

    expect(renderer.update(container, initial, labels)).toBe(true)
    const heading = container.children[0]
    const tail = container.children[1]

    expect(renderer.update(container, [initial[0]!, block("1", "tail-b", "<p>Tail B</p>", "live")], labels)).toBe(
      true,
    )
    expect(container.children[0]).toBe(heading)
    expect(container.children[1]).not.toBe(tail)
    expect(container.textContent).toBe("HeadingTail B")
  })

  test("promotes an unchanged tail and appends the next block without wrappers", () => {
    const container = document.createElement("div")
    document.body.append(container)
    const renderer = createIncrementalMarkdown(() => {})
    const heading = block("0", "heading", "<h2>Heading</h2>")
    const paragraph = block("1", "paragraph", "<p>Stable</p>", "live")

    renderer.update(container, [heading, paragraph], labels)
    const stable = container.children[1]
    renderer.update(container, [heading, { ...paragraph, mode: "full" }, block("2", "tail", "<ul><li>Next</li></ul>", "live")], labels)

    expect(container.children[1]).toBe(stable)
    expect(Array.from(container.children).map((child) => child.tagName)).toEqual(["H2", "P", "UL"])
  })

  test("runs streaming lifecycle hooks only when incremental rendering handles the update", () => {
    const container = document.createElement("div")
    document.body.append(container)
    const calls: string[] = []
    const renderer = createIncrementalMarkdown<string>(() => {}, {
      cancel: () => calls.push("cancel"),
      ready: (_container, _labels, context) => calls.push(context),
    })
    const blocks = [block("0", "stable", "<p>Stable</p>"), block("1", "tail", "<p>Tail</p>", "live")]

    expect(renderer.render(false, container, blocks, labels, "ready")).toBe(false)
    expect(calls).toEqual([])
    expect(renderer.render(true, container, blocks, labels, "ready")).toBe(true)
    expect(calls).toEqual(["cancel", "ready"])
  })

  test("falls back when there is no stable prefix", () => {
    const container = document.createElement("div")
    const renderer = createIncrementalMarkdown(() => {})

    expect(renderer.update(container, [block("0", "tail", "<p>Tail</p>", "live")], labels)).toBe(false)
    expect(
      renderer.update(
        container,
        [block("0", "live-a", "<p>A</p>", "live"), block("1", "live-b", "<p>B</p>", "live")],
        labels,
      ),
    ).toBe(false)
  })
})
