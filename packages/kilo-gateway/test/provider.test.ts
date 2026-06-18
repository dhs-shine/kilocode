import { describe, expect, test } from "bun:test"
import { addDataCollection, buildRequestHeaders } from "../src/provider"

describe("Kilo provider request headers", () => {
  test("request headers override provider defaults", () => {
    const headers = buildRequestHeaders(
      {
        "content-type": "application/json",
        "x-kilocode-feature": "vscode-extension",
        "x-default-only": "kept",
      },
      {
        "x-kilocode-feature": "agent-manager",
        "x-request-only": "kept-too",
      },
    )

    expect(headers.get("content-type")).toBe("application/json")
    expect(headers.get("x-kilocode-feature")).toBe("agent-manager")
    expect(headers.get("x-default-only")).toBe("kept")
    expect(headers.get("x-request-only")).toBe("kept-too")
  })
})

describe("Kilo provider request body", () => {
  test("denies data collection while preserving provider routing", () => {
    const body = JSON.stringify({
      model: "anthropic/claude-sonnet-4",
      provider: { order: ["anthropic"] },
    })
    const result = addDataCollection(body, "deny")

    expect(JSON.parse(result as string)).toEqual({
      model: "anthropic/claude-sonnet-4",
      provider: {
        order: ["anthropic"],
        data_collection: "deny",
      },
    })
  })

  test("leaves the request body unchanged without a preference", () => {
    const body = JSON.stringify({ model: "anthropic/claude-sonnet-4" })
    expect(addDataCollection(body)).toBe(body)
  })
})
