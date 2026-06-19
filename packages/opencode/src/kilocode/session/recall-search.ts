import path from "path"
import { and, asc, desc, eq, gt, gte, inArray, lte, sql } from "drizzle-orm"
import { Database } from "@/storage/db"
import { MessageTable, PartTable, SessionTable } from "@/session/session.sql"
import type { SessionID } from "@/session/schema"
import { Filesystem } from "@/util/filesystem"
import { ProjectTable } from "@/project/project.sql"
import { ProjectID } from "@/project/schema"

export namespace RecallSearch {
  const SESSION_BATCH = 64
  const PART_BATCH = 256
  const MAX_QUERY = 256
  const MAX_TERMS = 12
  const MAX_SNIPPETS = 3
  const SNIPPET_CHARS = 360
  const SNIPPET_CONTEXT = 120

  export type Source = "user" | "assistant" | "reference" | "error"

  export type Match = {
    source: Source
    partID: string
    text: string
  }

  export type Result = {
    id: string
    title: string
    directory: string
    updated: number
    matches: Match[]
  }

  export type Output = {
    results: Result[]
    sessions: number
    parts: number
  }

  type Candidate = Match & {
    mask: number
    phrase: boolean
  }

  type Item = Result & {
    phrase: number
    titleMask: number
    userMask: number
    assistantMask: number
    referenceMask: number
    errorMask: number
    mask: number
    candidates: Candidate[]
  }

  export async function search(input: {
    query: string
    projectID: string
    directories: string[]
    limit?: number
    signal?: AbortSignal
  }): Promise<Output> {
    const parsed = parse(input.query)
    const limit = input.limit ?? 20
    if (!Number.isInteger(limit) || limit < 1 || limit > 50) {
      throw new Error("Search result limits must be integers from 1 to 50")
    }

    const roots = [...new Set(input.directories.map(Filesystem.resolve))]
    if (roots.length === 0) return { results: [], sessions: 0, parts: 0 }

    const projects = new Set(family(input.projectID))
    const anchor = Database.use((db) =>
      db.select({ id: SessionTable.id }).from(SessionTable).orderBy(asc(SessionTable.id)).limit(1).get(),
    )
    if (!anchor) return { results: [], sessions: 0, parts: 0 }

    const rowid = sql<number>`${PartTable}.rowid`
    const last = Database.use((db) => db.select({ rowid }).from(PartTable).orderBy(desc(rowid)).limit(1).get())
    const full = (1 << parsed.terms.length) - 1
    const items = new Map<string, Item>()
    let cursor: SessionID | undefined
    let sessions = 0

    while (true) {
      abort(input.signal)
      const rows = Database.use((db) =>
        db
          .select({
            id: SessionTable.id,
            projectID: SessionTable.project_id,
            title: SessionTable.title,
            directory: SessionTable.directory,
            updated: SessionTable.time_updated,
          })
          .from(SessionTable)
          .where(and(cursor ? gt(SessionTable.id, cursor) : undefined, gte(SessionTable.id, anchor.id)))
          .orderBy(asc(SessionTable.id))
          .limit(SESSION_BATCH)
          .all(),
      )
      if (rows.length === 0) break

      cursor = rows.at(-1)!.id
      for (const row of rows) {
        if (!projects.has(row.projectID)) continue
        const directory = Filesystem.resolve(row.directory)
        if (!roots.some((root) => Filesystem.contains(root, directory))) continue

        const title = fold(row.title)
        const titleMask = mask(title, parsed.terms)
        items.set(row.id, {
          id: row.id,
          title: row.title,
          directory: row.directory,
          updated: row.updated,
          matches: [],
          phrase: title.includes(parsed.phrase) ? 5 : 0,
          titleMask,
          userMask: 0,
          assistantMask: 0,
          referenceMask: 0,
          errorMask: 0,
          mask: titleMask,
          candidates: [],
        })
        sessions++
      }
      if (rows.length < SESSION_BATCH) break
      await pause()
    }

    let part = 0
    let parts = 0
    const end = last?.rowid ?? 0
    while (part < end && items.size > 0) {
      abort(input.signal)
      const page = Database.use((db) =>
        db
          .select({ rowid, id: PartTable.id, sessionID: PartTable.session_id })
          .from(PartTable)
          .where(and(gt(rowid, part), lte(rowid, end)))
          .orderBy(asc(rowid))
          .limit(PART_BATCH)
          .all(),
      )
      if (page.length === 0) break

      part = page.at(-1)!.rowid
      const selected = page.filter((entry) => items.has(entry.sessionID))
      parts += selected.length
      if (selected.length > 0) {
        const rows = Database.use((db) =>
          db
            .select({
              id: PartTable.id,
              sessionID: PartTable.session_id,
              source: source(),
              text: content(),
            })
            .from(PartTable)
            .innerJoin(
              MessageTable,
              and(eq(MessageTable.id, PartTable.message_id), eq(MessageTable.session_id, PartTable.session_id)),
            )
            .where(
              inArray(
                PartTable.id,
                selected.map((entry) => entry.id),
              ),
            )
            .all(),
        )

        for (const row of rows) {
          if (!row.text) continue
          const normalized = fold(row.text)
          const matched = mask(normalized, parsed.terms)
          if (matched === 0) continue

          const item = items.get(row.sessionID)
          if (!item) continue
          item.mask |= matched
          if (row.source === "user") item.userMask |= matched
          if (row.source === "assistant") item.assistantMask |= matched
          if (row.source === "reference") item.referenceMask |= matched
          if (row.source === "error") item.errorMask |= matched

          const phrase = normalized.indexOf(parsed.phrase)
          const position = phrase >= 0 ? phrase : first(normalized, parsed.terms)
          item.phrase = Math.max(item.phrase, phrase >= 0 ? weight(row.source) : 0)
          candidate(item.candidates, {
            source: row.source,
            partID: row.id,
            text: excerpt(row.text, position),
            mask: matched,
            phrase: phrase >= 0,
          })
        }
      }
      if (page.length < PART_BATCH) break
      await pause()
    }

    const best: Item[] = []
    for (const item of items.values()) {
      if ((item.mask & full) !== full) continue
      item.matches = snippets(item, full)
      best.push(item)
      best.sort(compare)
      if (best.length > limit) best.pop()
    }

    return {
      results: best.map(
        ({
          phrase: _phrase,
          titleMask: _titleMask,
          userMask: _userMask,
          assistantMask: _assistantMask,
          referenceMask: _referenceMask,
          errorMask: _errorMask,
          mask: _mask,
          candidates: _candidates,
          ...item
        }) => item,
      ),
      sessions,
      parts,
    }
  }

  function family(id: string) {
    const row = Database.use((db) =>
      db
        .select({ worktree: ProjectTable.worktree })
        .from(ProjectTable)
        .where(eq(ProjectTable.id, ProjectID.make(id)))
        .get(),
    )
    const root = row?.worktree ? Filesystem.resolve(row.worktree) : undefined
    if (!root || root === path.parse(root).root) return [id]
    const ids = Database.use((db) =>
      db
        .select({ id: ProjectTable.id })
        .from(ProjectTable)
        .where(eq(ProjectTable.worktree, root))
        .all()
        .map((item) => item.id),
    )
    return ids.length ? ids : [id]
  }

  function parse(query: string) {
    const value = query.trim()
    if (!value) throw new Error("The 'query' parameter is required when mode is 'search'")
    if (value.length > MAX_QUERY) throw new Error(`Search queries cannot exceed ${MAX_QUERY} characters`)
    const phrase = fold(value).replace(/\s+/g, " ")
    const terms = [...new Set(phrase.split(" ").filter(Boolean))]
    if (terms.length > MAX_TERMS) throw new Error(`Search queries cannot exceed ${MAX_TERMS} terms`)
    return { phrase, terms }
  }

  function content() {
    const role = sql<string>`json_extract(${MessageTable.data}, '$.role')`
    return sql<string>`case
      when json_extract(${PartTable.data}, '$.type') = 'text'
        and ${role} in ('user', 'assistant')
        and coalesce(json_extract(${PartTable.data}, '$.synthetic'), 0) = 0
        and coalesce(json_extract(${PartTable.data}, '$.ignored'), 0) = 0
        then coalesce(json_extract(${PartTable.data}, '$.text'), '')
      when json_extract(${PartTable.data}, '$.type') = 'file' then trim(
        coalesce(json_extract(${PartTable.data}, '$.filename'), '') || ' ' ||
        coalesce(json_extract(${PartTable.data}, '$.source.path'), '') || ' ' ||
        coalesce(json_extract(${PartTable.data}, '$.source.name'), '')
      )
      when json_extract(${PartTable.data}, '$.type') = 'tool'
        and json_extract(${PartTable.data}, '$.state.status') = 'error'
        then coalesce(json_extract(${PartTable.data}, '$.state.error'), '')
      else ''
    end`
  }

  function source() {
    const kind = sql<string>`json_extract(${PartTable.data}, '$.type')`
    const role = sql<Source>`json_extract(${MessageTable.data}, '$.role')`
    return sql<Source>`case when ${kind} = 'text' then ${role} when ${kind} = 'file' then 'reference' else 'error' end`
  }

  function fold(value: string) {
    return value.normalize("NFKC").toLowerCase()
  }

  function mask(value: string, terms: string[]) {
    return terms.reduce((result, term, index) => result | (value.includes(term) ? 1 << index : 0), 0)
  }

  function first(value: string, terms: string[]) {
    return terms.reduce((result, term) => {
      const position = value.indexOf(term)
      if (position < 0) return result
      return result < 0 ? position : Math.min(result, position)
    }, -1)
  }

  function bits(value: number) {
    let count = 0
    for (let mask = value; mask > 0; mask >>>= 1) count += mask & 1
    return count
  }

  function weight(source: Source) {
    if (source === "user") return 4
    if (source === "assistant") return 3
    if (source === "reference") return 2
    return 1
  }

  function candidate(items: Candidate[], item: Candidate) {
    items.push(item)
    items.sort((a, b) => {
      if (a.phrase !== b.phrase) return Number(b.phrase) - Number(a.phrase)
      if (weight(a.source) !== weight(b.source)) return weight(b.source) - weight(a.source)
      return bits(b.mask) - bits(a.mask)
    })
    const kept: Candidate[] = []
    let covered = 0
    for (const value of items) {
      if ((value.mask & ~covered) === 0) continue
      kept.push(value)
      covered |= value.mask
    }
    items.splice(0, items.length, ...kept)
  }

  function snippets(item: Item, full: number) {
    const result: Match[] = []
    let missing = full & ~item.titleMask
    for (const value of item.candidates) {
      if (result.length >= MAX_SNIPPETS) break
      if (missing && (value.mask & missing) === 0) continue
      result.push({ source: value.source, partID: value.partID, text: value.text })
      missing &= ~value.mask
    }
    if (result.length === 0 && item.candidates[0]) {
      const value = item.candidates[0]
      result.push({ source: value.source, partID: value.partID, text: value.text })
    }
    return result
  }

  function compare(a: Item, b: Item) {
    if (a.phrase !== b.phrase) return b.phrase - a.phrase
    if (bits(a.titleMask) !== bits(b.titleMask)) return bits(b.titleMask) - bits(a.titleMask)
    if (bits(a.userMask) !== bits(b.userMask)) return bits(b.userMask) - bits(a.userMask)
    if (bits(a.assistantMask) !== bits(b.assistantMask)) return bits(b.assistantMask) - bits(a.assistantMask)
    if (bits(a.referenceMask) !== bits(b.referenceMask)) return bits(b.referenceMask) - bits(a.referenceMask)
    if (bits(a.errorMask) !== bits(b.errorMask)) return bits(b.errorMask) - bits(a.errorMask)
    if (a.updated !== b.updated) return b.updated - a.updated
    return a.id.localeCompare(b.id)
  }

  function excerpt(text: string, position: number) {
    const start = Math.max(0, position - SNIPPET_CONTEXT)
    const value = text.slice(start, start + SNIPPET_CHARS).trim()
    return `${start > 0 ? "..." : ""}${value}${start + SNIPPET_CHARS < text.length ? "..." : ""}`
  }

  function abort(signal?: AbortSignal) {
    if (!signal?.aborted) return
    throw signal.reason ?? new Error("Recall search aborted")
  }

  function pause() {
    return new Promise<void>((resolve) => setTimeout(resolve, 0))
  }
}
