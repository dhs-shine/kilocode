import { describe, expect, test } from "bun:test"
import {
  createVimState,
  enterNormal,
  handleNormalKey,
  lineEnd,
  lineStart,
  clampNormal,
  type VimDoc,
  type VimKey,
} from "@tui/component/prompt/vim"

/** In-memory document implementing the engine's VimDoc contract. */
class MockDoc implements VimDoc {
  private _text: string
  private _cursor: number
  private history: { text: string; cursor: number }[] = []
  private future: { text: string; cursor: number }[] = []

  constructor(text = "", cursor = 0) {
    this._text = text
    this._cursor = cursor
  }

  get text() {
    return this._text
  }
  get cursor() {
    return this._cursor
  }
  setCursor(offset: number) {
    this._cursor = Math.max(0, Math.min(offset, this._text.length))
  }
  private snapshot() {
    this.history.push({ text: this._text, cursor: this._cursor })
    this.future = []
  }
  insert(offset: number, value: string) {
    this.snapshot()
    this._text = this._text.slice(0, offset) + value + this._text.slice(offset)
    this._cursor = offset + value.length
  }
  remove(start: number, end: number) {
    this.snapshot()
    const removed = this._text.slice(start, end)
    this._text = this._text.slice(0, start) + this._text.slice(end)
    this._cursor = start
    return removed
  }
  undo() {
    const prev = this.history.pop()
    if (!prev) return
    this.future.push({ text: this._text, cursor: this._cursor })
    this._text = prev.text
    this._cursor = prev.cursor
  }
  redo() {
    const next = this.future.pop()
    if (!next) return
    this.history.push({ text: this._text, cursor: this._cursor })
    this._text = next.text
    this._cursor = next.cursor
  }
}

/** Feed a sequence of single-char keys (with optional ctrl via "<C-x>"). */
function feed(doc: MockDoc, state: ReturnType<typeof createVimState>, keys: string) {
  let i = 0
  while (i < keys.length) {
    let vk: VimKey
    if (keys.startsWith("<C-", i)) {
      const close = keys.indexOf(">", i)
      vk = { key: keys.slice(i + 3, close), ctrl: true }
      i = close + 1
    } else if (keys.startsWith("<esc>", i)) {
      vk = { key: "escape" }
      i += 5
    } else {
      vk = { key: keys[i]! }
      i += 1
    }
    handleNormalKey(doc, state, vk)
  }
}

describe("vim line helpers", () => {
  test("lineStart / lineEnd", () => {
    const t = "abc\ndef\nghi"
    expect(lineStart(t, 5)).toBe(4)
    expect(lineEnd(t, 5)).toBe(7)
    expect(lineStart(t, 0)).toBe(0)
    expect(lineEnd(t, 0)).toBe(3)
  })

  test("clampNormal keeps cursor off the trailing newline", () => {
    const t = "abc\ndef"
    expect(clampNormal(t, 3)).toBe(2)
    expect(clampNormal(t, 7)).toBe(6)
    expect(clampNormal("", 0)).toBe(0)
  })
})

describe("vim motions", () => {
  test("h and l move within the line and clamp", () => {
    const doc = new MockDoc("hello", 0)
    const state = createVimState("normal")
    feed(doc, state, "lll")
    expect(doc.cursor).toBe(3)
    feed(doc, state, "h")
    expect(doc.cursor).toBe(2)
    feed(doc, state, "hhhhh")
    expect(doc.cursor).toBe(0)
  })

  test("counts repeat motions (3l)", () => {
    const doc = new MockDoc("hello world", 0)
    const state = createVimState("normal")
    feed(doc, state, "3l")
    expect(doc.cursor).toBe(3)
  })

  test("w / b word motions", () => {
    const doc = new MockDoc("foo bar baz", 0)
    const state = createVimState("normal")
    feed(doc, state, "w")
    expect(doc.cursor).toBe(4)
    feed(doc, state, "w")
    expect(doc.cursor).toBe(8)
    feed(doc, state, "b")
    expect(doc.cursor).toBe(4)
  })

  test("e moves to end of word", () => {
    const doc = new MockDoc("foo bar", 0)
    const state = createVimState("normal")
    feed(doc, state, "e")
    expect(doc.cursor).toBe(2)
  })

  test("0 and $ jump to line bounds", () => {
    const doc = new MockDoc("hello world", 3)
    const state = createVimState("normal")
    feed(doc, state, "$")
    expect(doc.cursor).toBe(10)
    feed(doc, state, "0")
    expect(doc.cursor).toBe(0)
  })

  test("gg and G jump across lines", () => {
    const doc = new MockDoc("one\ntwo\nthree", 0)
    const state = createVimState("normal")
    feed(doc, state, "G")
    expect(lineStart(doc.text, doc.cursor)).toBe(8)
    feed(doc, state, "gg")
    expect(doc.cursor).toBe(0)
  })

  test("j and k preserve column", () => {
    const doc = new MockDoc("abcd\nef\nghij", 2)
    const state = createVimState("normal")
    feed(doc, state, "j")
    // second line "ef" is shorter; clamp to last char
    expect(doc.cursor).toBe(6)
    feed(doc, state, "j")
    expect(doc.cursor).toBe(10)
  })
})

describe("vim edits", () => {
  test("x deletes the char under the cursor", () => {
    const doc = new MockDoc("hello", 1)
    const state = createVimState("normal")
    feed(doc, state, "x")
    expect(doc.text).toBe("hllo")
    expect(doc.cursor).toBe(1)
  })

  test("2x deletes two chars", () => {
    const doc = new MockDoc("hello", 0)
    const state = createVimState("normal")
    feed(doc, state, "2x")
    expect(doc.text).toBe("llo")
  })

  test("dw deletes a word", () => {
    const doc = new MockDoc("foo bar baz", 0)
    const state = createVimState("normal")
    feed(doc, state, "dw")
    expect(doc.text).toBe("bar baz")
  })

  test("dd deletes the current line", () => {
    const doc = new MockDoc("one\ntwo\nthree", 4)
    const state = createVimState("normal")
    feed(doc, state, "dd")
    expect(doc.text).toBe("one\nthree")
  })

  test("D deletes to end of line", () => {
    const doc = new MockDoc("hello world", 5)
    const state = createVimState("normal")
    feed(doc, state, "D")
    expect(doc.text).toBe("hello")
  })

  test("cw deletes word and enters insert mode", () => {
    const doc = new MockDoc("foo bar", 0)
    const state = createVimState("normal")
    feed(doc, state, "cw")
    expect(doc.text).toBe(" bar")
    expect(state.mode).toBe("insert")
  })

  test("r replaces a single char", () => {
    const doc = new MockDoc("cat", 0)
    const state = createVimState("normal")
    feed(doc, state, "rb")
    expect(doc.text).toBe("bat")
    expect(state.mode).toBe("normal")
  })

  test("u undoes and <C-r> redoes", () => {
    const doc = new MockDoc("hello", 0)
    const state = createVimState("normal")
    feed(doc, state, "x")
    expect(doc.text).toBe("ello")
    feed(doc, state, "u")
    expect(doc.text).toBe("hello")
    feed(doc, state, "<C-r>")
    expect(doc.text).toBe("ello")
  })
})

describe("vim yank/paste", () => {
  test("yy then p duplicates the line below", () => {
    const doc = new MockDoc("one\ntwo", 0)
    const state = createVimState("normal")
    feed(doc, state, "yyp")
    expect(doc.text).toBe("one\none\ntwo")
  })

  test("dw then p pastes the deleted word", () => {
    const doc = new MockDoc("foo bar", 0)
    const state = createVimState("normal")
    feed(doc, state, "dw") // text => "bar", register "foo "
    feed(doc, state, "p")
    expect(doc.text).toContain("foo")
  })
})

describe("vim mode transitions", () => {
  test("i enters insert mode", () => {
    const doc = new MockDoc("abc", 1)
    const state = createVimState("normal")
    feed(doc, state, "i")
    expect(state.mode).toBe("insert")
  })

  test("a appends after cursor", () => {
    const doc = new MockDoc("abc", 0)
    const state = createVimState("normal")
    feed(doc, state, "a")
    expect(doc.cursor).toBe(1)
    expect(state.mode).toBe("insert")
  })

  test("A jumps to end of line", () => {
    const doc = new MockDoc("abc", 0)
    const state = createVimState("normal")
    feed(doc, state, "A")
    expect(doc.cursor).toBe(3)
    expect(state.mode).toBe("insert")
  })

  test("o opens a line below", () => {
    const doc = new MockDoc("abc", 1)
    const state = createVimState("normal")
    feed(doc, state, "o")
    expect(doc.text).toBe("abc\n")
    expect(doc.cursor).toBe(4)
  })

  test("O opens a line above", () => {
    const doc = new MockDoc("abc", 1)
    const state = createVimState("normal")
    feed(doc, state, "O")
    expect(doc.text).toBe("\nabc")
    expect(doc.cursor).toBe(0)
  })

  test("enterNormal clamps cursor one left", () => {
    const doc = new MockDoc("abc", 3)
    const state = createVimState("insert")
    enterNormal(doc, state)
    expect(state.mode).toBe("normal")
    expect(doc.cursor).toBe(2)
  })

  test("escape clears a pending operator", () => {
    const doc = new MockDoc("foo bar", 0)
    const state = createVimState("normal")
    feed(doc, state, "d")
    expect(state.operator).toBe("d")
    feed(doc, state, "<esc>")
    expect(state.operator).toBeUndefined()
    expect(doc.text).toBe("foo bar")
  })

  test("normal-mode letters never leak into the document", () => {
    const doc = new MockDoc("abc", 0)
    const state = createVimState("normal")
    feed(doc, state, "zZqQ")
    expect(doc.text).toBe("abc")
  })
})
