import { describe, expect, it } from "bun:test"
import {
  getMigrationCache,
  type MigrationCache,
  type MigrationCacheEntry,
} from "../../../src/kilo-provider/handlers/migration"

const legacy = {
  hasData: false,
  providers: [],
  mcpServers: [],
  customModes: [],
}

describe("migration cache", () => {
  it("isolates entries by operation and source", () => {
    const cache: MigrationCache = new Map()
    const entry: MigrationCacheEntry = { operationId: "new", source: "legacy", data: legacy }
    cache.set("new", entry)

    expect(getMigrationCache(cache, "legacy", "new")).toBe(entry)
    expect(getMigrationCache(cache, "roo", "new")).toBeUndefined()
    expect(getMigrationCache(cache, "legacy", "stale")).toBeUndefined()
  })

  it("retains an empty Roo discovery for its operation", () => {
    const cache: MigrationCache = new Map()
    const entry: MigrationCacheEntry = { operationId: "empty", source: "roo", data: null }
    cache.set("empty", entry)

    expect(getMigrationCache(cache, "roo", "empty")).toBe(entry)
    expect(getMigrationCache(cache, "roo", "empty")?.data).toBeNull()
  })
})
