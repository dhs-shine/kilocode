import { afterEach, beforeEach, describe, expect, it } from "bun:test"
import * as vscode from "vscode"
import { createSessionID } from "../../src/legacy-migration/sessions/lib/ids"
import { parseSession } from "../../src/legacy-migration/sessions/parser"
import { detectRooCodeSessions } from "../../src/roo-import/service"

const enc = new TextEncoder()
const first = "/storage/roovscode.roo-cline/tasks"
const second = "/storage/rooveterinaryinc.roo-cline/tasks"
const id = "1781613537275"
const other = "1781613537276"
const special = "__proto__"

type Fs = typeof vscode.workspace.fs
const fs = vscode.workspace.fs as Fs
const original = { readDirectory: fs.readDirectory, readFile: fs.readFile, stat: fs.stat }

describe("roo import", () => {
  beforeEach(() => {
    const files = new Map([
      [`${first}/${id}/api_conversation_history.json`, JSON.stringify([{ role: "user", content: "First root" }])],
      [`${first}/${special}/api_conversation_history.json`, JSON.stringify([{ role: "user", content: "Special" }])],
      [
        `${first}/${id}/history_item.json`,
        JSON.stringify({ id, ts: Number(id), task: "First root", workspace: "/repo", mode: "architect" }),
      ],
      [`${second}/${id}/api_conversation_history.json`, JSON.stringify([{ role: "user", content: "Duplicate" }])],
      [`${second}/${other}/ui_messages.json`, JSON.stringify([{ type: "say", text: "UI only" }])],
      [`${second}/bad/api_conversation_history.json`, "not json"],
    ])

    fs.readDirectory = async (uri) => {
      if (uri.fsPath === first) {
        return [
          [id, vscode.FileType.Directory],
          [special, vscode.FileType.Directory],
        ]
      }
      if (uri.fsPath === second) {
        return [
          [id, vscode.FileType.Directory],
          [other, vscode.FileType.Directory],
          ["bad", vscode.FileType.Directory],
        ]
      }
      throw new Error(`missing dir ${uri.fsPath}`)
    }
    fs.stat = async (uri) => {
      if (files.has(uri.fsPath)) return { type: vscode.FileType.File, ctime: 0, mtime: 0, size: 1 }
      throw new Error(`missing file ${uri.fsPath}`)
    }
    fs.readFile = async (uri) => {
      const value = files.get(uri.fsPath)
      if (value) return enc.encode(value)
      throw new Error(`missing file ${uri.fsPath}`)
    }
  })

  afterEach(() => {
    fs.readDirectory = original.readDirectory
    fs.readFile = original.readFile
    fs.stat = original.stat
  })

  it("merges all roots, deterministically deduplicates, and diagnoses skipped tasks", async () => {
    const source = await detectRooCodeSessions({ globalStorageUri: { fsPath: "/storage/kilocode.kilo-code" } } as never)

    expect(source?.sessions).toEqual([
      { id, title: "First root", directory: "/repo", time: Number(id) },
      { id: special, title: "Special", directory: "", time: 0 },
    ])
    expect(source?.catalog.get(id)?.source).toMatchObject({ id, dir: first, namespace: "roo" })
    expect(source?.catalog.get(special)?.source).toMatchObject({ id: special, dir: first, namespace: "roo" })
    expect(source?.catalog.get(id)?.source.item).toMatchObject({ mode: "architect" })
    expect(source?.diagnostics.map((item) => [item.id, item.reason])).toEqual([
      [other, "ui-only"],
      ["bad", "malformed"],
    ])
  })

  it("namespaces generated Roo IDs without changing the visible session slug", async () => {
    const source = await detectRooCodeSessions({ globalStorageUri: { fsPath: "/storage/kilocode.kilo-code" } } as never)
    const entry = source?.catalog.get(id)
    expect(entry).toBeDefined()

    const payload = await parseSession(
      id,
      entry!.source.dir,
      entry!.source.item,
      [{ role: "user", content: "Hi" }],
      `roo:${id}`,
    )

    expect(payload.session.id).toBe(createSessionID(`roo:${id}`))
    expect(payload.session.slug).toBe(id)
    expect(payload.messages[0].sessionID).toBe(createSessionID(`roo:${id}`))
  })
})
