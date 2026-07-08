import { describe, expect, it } from "bun:test"
import { createRoot } from "solid-js"
import { useFileMention } from "../../webview-ui/src/hooks/useFileMention"
import { FILE_PICKER_RESULT } from "../../webview-ui/src/hooks/file-mention-utils"
import type { ExtensionMessage, WebviewMessage } from "../../webview-ui/src/types/messages"

declare global {
  // eslint-disable-next-line no-var
  var document: { execCommand: (commandId: string, showUI?: boolean, value?: string) => boolean }
}

const hadDoc = "document" in globalThis
const originalDoc = hadDoc ? globalThis.document : undefined

function mockDocument() {
  globalThis.document = { execCommand: () => true }
}

function restoreDocument() {
  if (hadDoc && originalDoc) {
    globalThis.document = originalDoc
  } else {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    delete (globalThis as any).document
  }
}

const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

function textarea(value: string, cursor: number, dir: "ltr" | "rtl") {
  const state = { cursor }
  return {
    value,
    get selectionStart() {
      return state.cursor
    },
    get selectionEnd() {
      return state.cursor
    },
    matches: (selector: string) => selector === `:dir(${dir})`,
    setSelectionRange: (start: number) => {
      state.cursor = start
    },
  } as unknown as HTMLTextAreaElement
}

function key(key: "ArrowLeft" | "ArrowRight") {
  const state = { prevented: 0 }
  return {
    state,
    event: {
      key,
      preventDefault: () => state.prevented++,
    } as unknown as KeyboardEvent,
  }
}

describe("useFileMention", () => {
  it("keeps previous file results visible while the next search is pending", async () => {
    const posted: WebviewMessage[] = []
    const handlers = new Set<(message: ExtensionMessage) => void>()
    const ctx = {
      postMessage: (message: WebviewMessage) => posted.push(message),
      onMessage: (handler: (message: ExtensionMessage) => void) => {
        handlers.add(handler)
        return () => handlers.delete(handler)
      },
    }

    const dispose: { fn?: () => void } = {}
    const mention = createRoot((root) => {
      dispose.fn = root
      return useFileMention(ctx, undefined, () => false)
    })

    mention.onInput("@e", 2)
    await wait(170)

    const first = posted.at(-1)
    expect(first?.type).toBe("requestFileSearch")
    expect(first).toMatchObject({ query: "e", requestId: "file-search-1" })

    for (const handler of handlers) {
      handler({
        type: "fileSearchResult",
        requestId: "file-search-1",
        dir: "/repo",
        paths: ["packages/kilo-vscode/src/extension.ts"],
        items: [{ path: "packages/kilo-vscode/src/extension.ts", type: "opened-file" }],
      })
    }

    expect(mention.mentionResults()).toEqual([
      FILE_PICKER_RESULT,
      { type: "opened-file", value: "packages/kilo-vscode/src/extension.ts" },
    ])

    mention.onInput("@ex", 3)

    expect(mention.mentionResults()).toEqual([
      FILE_PICKER_RESULT,
      { type: "opened-file", value: "packages/kilo-vscode/src/extension.ts" },
    ])

    dispose.fn?.()
  })

  it("does not keep stale file results visible for unrelated queries", async () => {
    const posted: WebviewMessage[] = []
    const handlers = new Set<(message: ExtensionMessage) => void>()
    const ctx = {
      postMessage: (message: WebviewMessage) => posted.push(message),
      onMessage: (handler: (message: ExtensionMessage) => void) => {
        handlers.add(handler)
        return () => handlers.delete(handler)
      },
    }

    const dispose: { fn?: () => void } = {}
    const mention = createRoot((root) => {
      dispose.fn = root
      return useFileMention(ctx, undefined, () => false)
    })

    mention.onInput("@read", 5)
    await wait(170)

    for (const handler of handlers) {
      handler({
        type: "fileSearchResult",
        requestId: "file-search-1",
        dir: "/repo",
        paths: ["README.md"],
        items: [{ path: "README.md", type: "file" }],
      })
    }

    mention.onInput("@zz", 3)

    expect(mention.mentionResults()).toEqual([FILE_PICKER_RESULT])

    dispose.fn?.()
  })

  it("seedFromText populates knownPaths so mentions are recognized in pre-filled text", () => {
    const ctx = {
      postMessage: () => {},
      onMessage: () => () => {},
    }

    const dispose: { fn?: () => void } = {}
    const mention = createRoot((root) => {
      dispose.fn = root
      return useFileMention(ctx, undefined, () => false)
    })

    // Before seeding, no paths are known
    expect(mention.mentionedPaths().size).toBe(0)

    // Seed from text containing @mentions (simulates setChatBoxMessage after revert)
    mention.seedFromText("Say hi to @packages/plugin/tsconfig.json !")

    expect(mention.mentionedPaths().has("packages/plugin/tsconfig.json")).toBe(true)

    dispose.fn?.()
  })

  it("seedFromText handles multiple @mentions in one string", () => {
    const ctx = {
      postMessage: () => {},
      onMessage: () => () => {},
    }

    const dispose: { fn?: () => void } = {}
    const mention = createRoot((root) => {
      dispose.fn = root
      return useFileMention(ctx, undefined, () => false)
    })

    mention.seedFromText("check @src/a.ts and @src/b.tsx")

    expect(mention.mentionedPaths().has("src/a.ts")).toBe(true)
    expect(mention.mentionedPaths().has("src/b.tsx")).toBe(true)

    dispose.fn?.()
  })

  it("seedFromText ignores text without @mentions", () => {
    const ctx = {
      postMessage: () => {},
      onMessage: () => () => {},
    }

    const dispose: { fn?: () => void } = {}
    const mention = createRoot((root) => {
      dispose.fn = root
      return useFileMention(ctx, undefined, () => false)
    })

    mention.seedFromText("no mentions here")
    expect(mention.mentionedPaths().size).toBe(0)

    dispose.fn?.()
  })

  it("filters visible results synchronously while a new search is pending", async () => {
    const posted: WebviewMessage[] = []
    const handlers = new Set<(message: ExtensionMessage) => void>()
    const ctx = {
      postMessage: (message: WebviewMessage) => posted.push(message),
      onMessage: (handler: (message: ExtensionMessage) => void) => {
        handlers.add(handler)
        return () => handlers.delete(handler)
      },
    }

    const dispose: { fn?: () => void } = {}
    const mention = createRoot((root) => {
      dispose.fn = root
      return useFileMention(ctx, undefined, () => false)
    })

    mention.onInput("@g", 2)
    await wait(170)

    for (const handler of handlers) {
      handler({
        type: "fileSearchResult",
        requestId: "file-search-1",
        dir: "/repo",
        paths: ["README.md", "src/git.ts"],
        items: [
          { path: "README.md", type: "file" },
          { path: "src/git.ts", type: "file" },
        ],
      })
    }

    mention.onInput("@gi", 3)

    expect(mention.mentionResults()).toEqual([FILE_PICKER_RESULT, { type: "file", value: "src/git.ts" }])

    dispose.fn?.()
  })

  it("keeps mention block arrow navigation aligned with left-to-right prompt direction", async () => {
    const ctx = {
      postMessage: () => {},
      onMessage: () => () => {},
    }

    const dispose: { fn?: () => void } = {}
    const mention = createRoot((root) => {
      dispose.fn = root
      return useFileMention(ctx, undefined, () => false)
    })

    const text = "See @src/main.ts now"
    mention.addPaths(["src/main.ts"], "/repo")

    const right = key("ArrowRight")
    const input = textarea(text, "See ".length, "ltr")
    expect(mention.handleArrowKey(right.event, input)).toBe(true)
    expect(input.selectionStart).toBe("See @src/main.ts".length)
    expect(right.state.prevented).toBe(1)

    const left = key("ArrowLeft")
    expect(mention.handleArrowKey(left.event, input)).toBe(true)
    expect(input.selectionStart).toBe("See ".length)
    expect(left.state.prevented).toBe(1)

    dispose.fn?.()
  })

  it("keeps mention block arrow navigation aligned for right-to-left languages", async () => {
    const ctx = {
      postMessage: () => {},
      onMessage: () => () => {},
    }

    const dispose: { fn?: () => void } = {}
    const mention = createRoot((root) => {
      dispose.fn = root
      return useFileMention(ctx, undefined, () => false)
    })

    const text = "فایل @src/main.ts را ببین"
    mention.addPaths(["src/main.ts"], "/repo")

    const left = key("ArrowLeft")
    const input = textarea(text, "فایل ".length, "rtl")
    expect(mention.handleArrowKey(left.event, input)).toBe(true)
    expect(input.selectionStart).toBe("فایل @src/main.ts".length)
    expect(left.state.prevented).toBe(1)

    const right = key("ArrowRight")
    expect(mention.handleArrowKey(right.event, input)).toBe(true)
    expect(input.selectionStart).toBe("فایل ".length)
    expect(right.state.prevented).toBe(1)

    dispose.fn?.()
  })

  it("selecting file picker sends requestFilePicker and stores state", async () => {
    const posted: WebviewMessage[] = []
    const ctx = {
      postMessage: (message: WebviewMessage) => posted.push(message),
      onMessage: () => () => {},
    }

    const dispose: { fn?: () => void } = {}
    const mention = createRoot((root) => {
      dispose.fn = root
      return useFileMention(ctx, undefined, () => false)
    })

    const state = { value: "hello @b", cursor: 8 }
    const input = {
      value: state.value,
      get selectionStart() {
        return state.cursor
      },
      get selectionEnd() {
        return state.cursor
      },
      isConnected: true,
      setSelectionRange: (start: number, end: number) => {
        state.cursor = end
      },
      focus: () => {},
    } as unknown as HTMLTextAreaElement

    let execCalled = false
    mockDocument()
    globalThis.document.execCommand = () => {
      execCalled = true
      return true
    }

    try {
      mention.selectMention(
        { type: "file-picker", value: "file-picker", label: "Browse", description: "" },
        input,
        () => {},
      )
    } finally {
      restoreDocument()
    }

    expect(posted).toEqual([{ type: "requestFilePicker" }])
    expect(execCalled).toBe(false)

    dispose.fn?.()
  })

  it("insertFilePickerResult inserts the path at the stored position", () => {
    const ctx = {
      postMessage: () => {},
      onMessage: () => () => {},
    }

    const dispose: { fn?: () => void } = {}
    const mention = createRoot((root) => {
      dispose.fn = root
      return useFileMention(ctx, undefined, () => false)
    })

    const state = { value: "hello @b", cursor: 8, textSet: "" }
    const input = {
      get value() {
        return state.value
      },
      get selectionStart() {
        return state.cursor
      },
      get selectionEnd() {
        return state.cursor
      },
      isConnected: true,
      setSelectionRange: (start: number, end: number) => {
        state.value = state.value.slice(0, start) + state.value.slice(end)
        state.cursor = start
      },
      focus: () => {},
    } as unknown as HTMLTextAreaElement

    mockDocument()
    globalThis.document.execCommand = (_cmd: string, _show: boolean, val: string) => {
      state.value = state.value.slice(0, state.cursor) + val + state.value.slice(state.cursor)
      state.cursor = state.cursor + val.length
      return true
    }

    try {
      mention.selectMention(
        { type: "file-picker", value: "file-picker", label: "Browse", description: "" },
        input,
        (text: string) => {
          state.textSet = text
        },
      )
      mention.insertFilePickerResult("/outside/file.ts")
    } finally {
      restoreDocument()
    }

    expect(state.value).toBe("hello @/outside/file.ts ")
    expect(mention.mentionedPaths().has("/outside/file.ts")).toBe(true)
    expect(state.textSet).toBe("hello @/outside/file.ts ")

    dispose.fn?.()
  })

  it("insertFilePickerResult normalizes Windows backslashes to forward slashes", () => {
    const ctx = {
      postMessage: () => {},
      onMessage: () => () => {},
    }

    const dispose: { fn?: () => void } = {}
    const mention = createRoot((root) => {
      dispose.fn = root
      return useFileMention(ctx, undefined, () => false)
    })

    const state = { value: "hello @b", cursor: 8, textSet: "" }
    const input = {
      get value() {
        return state.value
      },
      get selectionStart() {
        return state.cursor
      },
      get selectionEnd() {
        return state.cursor
      },
      isConnected: true,
      setSelectionRange: (start: number, end: number) => {
        state.value = state.value.slice(0, start) + state.value.slice(end)
        state.cursor = start
      },
      focus: () => {},
    } as unknown as HTMLTextAreaElement

    mockDocument()
    globalThis.document.execCommand = (_cmd: string, _show: boolean, val: string) => {
      state.value = state.value.slice(0, state.cursor) + val + state.value.slice(state.cursor)
      state.cursor = state.cursor + val.length
      return true
    }

    try {
      mention.selectMention(
        { type: "file-picker", value: "file-picker", label: "Browse", description: "" },
        input,
        (text: string) => {
          state.textSet = text
        },
      )
      mention.insertFilePickerResult("C:\\Users\\file.ts")
    } finally {
      restoreDocument()
    }

    expect(state.value).toBe("hello @C:/Users/file.ts ")
    expect(mention.mentionedPaths().has("C:/Users/file.ts")).toBe(true)

    dispose.fn?.()
  })

  it("insertFilePickerResult with empty path cleans up state", () => {
    const ctx = {
      postMessage: () => {},
      onMessage: () => () => {},
    }

    const dispose: { fn?: () => void } = {}
    const mention = createRoot((root) => {
      dispose.fn = root
      return useFileMention(ctx, undefined, () => false)
    })

    const input = {
      value: "hello @b",
      selectionStart: 8,
      selectionEnd: 8,
      isConnected: true,
      setSelectionRange: () => {},
      focus: () => {},
    } as unknown as HTMLTextAreaElement

    mention.selectMention(
      { type: "file-picker", value: "file-picker", label: "Browse", description: "" },
      input,
      () => {},
    )
    mention.insertFilePickerResult("")

    expect(input.value).toBe("hello @b")

    dispose.fn?.()
  })
})
