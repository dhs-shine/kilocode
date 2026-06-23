import type { MarketplaceInstalledMetadata, MarketplaceItem } from "../../types/marketplace"

export function isInstalled(
  id: string,
  type: string,
  metadata: MarketplaceInstalledMetadata,
): "project" | "global" | false {
  return installedScopes(id, type, metadata)[0] ?? false
}

export function installedScopes(
  id: string,
  type: string,
  metadata: MarketplaceInstalledMetadata,
): ("project" | "global")[] {
  const scopes: ("project" | "global")[] = []
  const key = `${type}:${id}`
  if (metadata.project[key]?.type === type) scopes.push("project")
  if (metadata.global[key]?.type === type) scopes.push("global")
  return scopes
}

export function filterItems(
  items: MarketplaceItem[],
  metadata: MarketplaceInstalledMetadata,
  search: string,
  status: string,
  categories: string[],
): MarketplaceItem[] {
  const query = search.trim().toLowerCase()
  return items
    .filter((item) => {
      if (status === "installed" && !isInstalled(item.id, item.type, metadata)) return false
      if (status === "notInstalled" && isInstalled(item.id, item.type, metadata)) return false
      if (categories.length > 0 && !categories.includes(item.category)) return false
      if (!query) return true
      const skill = item.type === "skill" ? item : undefined
      return (
        item.id.toLowerCase().includes(query) ||
        item.name.toLowerCase().includes(query) ||
        item.description.toLowerCase().includes(query) ||
        item.category.toLowerCase().includes(query) ||
        item.type.includes(query) ||
        (item.author?.toLowerCase().includes(query) ?? false) ||
        (skill?.displayName.toLowerCase().includes(query) ?? false)
      )
    })
    .sort((a, b) => a.name.localeCompare(b.name))
}
