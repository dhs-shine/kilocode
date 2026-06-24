import { describe, expect, it, mock } from "bun:test"
import { detectMarketplaceRelevance } from "../../src/services/marketplace/relevance"
import type { MarketplaceItem } from "../../src/services/marketplace/types"

const items: MarketplaceItem[] = [
  {
    type: "agent",
    id: "angular",
    name: "Angular",
    description: "Angular specialist",
    category: "development",
    content: { mode: "all", description: "Angular specialist", prompt: "Help with Angular" },
    suggest_for: { filename: ["*.component.ts"] },
  },
  {
    type: "mcp",
    id: "jupyter",
    name: "Jupyter",
    description: "Jupyter notebooks",
    category: "data",
    url: "https://example.com",
    content: "{}",
    suggest_for: {
      filename: ["*.component.ts", "*.ipynb"],
      vscode_extension: ["ms-toolsai.jupyter"],
    },
  },
  {
    type: "skill",
    id: "unmatched",
    name: "Unmatched",
    displayName: "Unmatched",
    description: "No matching context",
    category: "development",
    displayCategory: "Development",
    githubUrl: "https://example.com",
    content: "https://example.com/skill.tar.gz",
    suggest_for: { filename: ["*.rs"] },
  },
]

describe("Marketplace relevance", () => {
  it("matches workspace files and installed extensions with deduplicated bounded searches", async () => {
    const find = mock(async (_workspace: string, pattern: string) => pattern === "*.component.ts")

    const relevance = await detectMarketplaceRelevance(items, "/repo", {
      extensions: ["MS-ToolsAI.Jupyter"],
      find,
    })

    expect(relevance).toEqual({
      "agent:angular": { filename: ["*.component.ts"] },
      "mcp:jupyter": {
        filename: ["*.component.ts"],
        vscodeExtension: ["ms-toolsai.jupyter"],
      },
    })
    expect(find).toHaveBeenCalledTimes(3)
    expect(find.mock.calls).toContainEqual(["/repo", "*.component.ts"])
    expect(find.mock.calls).toContainEqual(["/repo", "*.ipynb"])
    expect(find.mock.calls).toContainEqual(["/repo", "*.rs"])
  })

  it("still matches installed extensions without a workspace", async () => {
    const find = mock(async () => true)

    const relevance = await detectMarketplaceRelevance(items, undefined, {
      extensions: ["ms-toolsai.jupyter"],
      find,
    })

    expect(relevance).toEqual({ "mcp:jupyter": { vscodeExtension: ["ms-toolsai.jupyter"] } })
    expect(find).not.toHaveBeenCalled()
  })
})
