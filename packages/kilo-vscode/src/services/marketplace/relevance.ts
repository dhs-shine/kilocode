import * as vscode from "vscode"
import type { MarketplaceItem, MarketplaceRelevanceMetadata } from "./types"

const EXCLUDE = "**/{node_modules,.git,dist,build,out,.kilo,.opencode,.kilocode}/**"

interface RelevanceHost {
  extensions: readonly string[]
  find: (workspace: string, pattern: string) => Promise<boolean>
}

function context(): RelevanceHost {
  return {
    extensions: vscode.extensions.all.map((extension) => extension.id),
    find: async (workspace, pattern) => {
      const glob = new vscode.RelativePattern(workspace, `**/${pattern}`)
      return (await vscode.workspace.findFiles(glob, EXCLUDE, 1)).length > 0
    },
  }
}

export async function detectMarketplaceRelevance(
  items: MarketplaceItem[],
  workspace?: string,
  source: RelevanceHost = context(),
): Promise<MarketplaceRelevanceMetadata> {
  const patterns = Array.from(new Set(items.flatMap((item) => item.suggest_for?.filename ?? [])))
  const files = new Map<string, boolean>()

  if (workspace) {
    const batches = Array.from({ length: Math.ceil(patterns.length / 4) }, (_, index) =>
      patterns.slice(index * 4, index * 4 + 4),
    )
    for (const batch of batches) {
      await Promise.all(
        batch.map(async (pattern) => {
          const found = await source.find(workspace, pattern).catch((err: unknown) => {
            console.warn(`[Kilo New] Marketplace relevance scan failed for ${pattern}:`, err)
            return false
          })
          files.set(pattern, found)
        }),
      )
    }
  }

  const extensions = new Set(source.extensions.map((id) => id.toLowerCase()))
  return Object.fromEntries(
    items.flatMap((item) => {
      const filename = item.suggest_for?.filename?.filter((pattern) => files.get(pattern))
      const vscodeExtension = item.suggest_for?.vscode_extension?.filter((id) => extensions.has(id.toLowerCase()))
      if (!filename?.length && !vscodeExtension?.length) return []
      return [
        [
          `${item.type}:${item.id}`,
          {
            ...(filename?.length && { filename }),
            ...(vscodeExtension?.length && { vscodeExtension }),
          },
        ],
      ]
    }),
  )
}
