import { expect, test, type Page } from "@playwright/test"

const GLOBALS = "colorScheme:dark;theme:kilo-vscode;vscodeTheme:dark-modern"

function story(page: Page, id: string) {
  return page.goto(`/iframe.html?id=${id}&viewMode=story&globals=${GLOBALS}`, { waitUntil: "load" })
}

test.describe("history session accessibility", () => {
  test("opens a selected session through a standalone named row control", async ({ page }) => {
    await story(page, "history-sessionlist--with-items")

    const row = page.getByRole("button", { name: /Refactor authentication module.*Current session/ })
    await expect(row).toHaveAttribute("data-selected", "true")
    await expect(page.locator('[data-slot="list-item"] button')).toHaveCount(0)

    await row.focus()
    await page.keyboard.press("Enter")
    await expect(page.locator('[data-slot="selected-session"]')).toHaveText("s1")
  })

  test("announces the active filtered result before Enter opens it", async ({ page }) => {
    await story(page, "history-sessionlist--with-items")

    const search = page.getByPlaceholder("Search sessions...")
    await search.fill("screenshot")
    await expect(search).toBeFocused()
    await expect(page.locator('[data-slot="session-list-status"]')).toHaveText("Add screenshot test coverage")

    await page.keyboard.press("Enter")
    await expect(page.locator('[data-slot="selected-session"]')).toHaveText("s2")
  })

  test("focuses and activates separate named rename and delete controls", async ({ page }) => {
    await story(page, "history-sessionlist--with-items")

    const rename = page.getByRole("button", { name: "Rename: Add screenshot test coverage" })
    await rename.focus()
    await expect(rename).toBeFocused()
    await page.keyboard.press("Enter")
    await expect(page.getByRole("textbox", { name: "Rename" })).toBeFocused()

    await story(page, "history-sessionlist--with-items")
    const remove = page.getByRole("button", { name: "Delete session: Add screenshot test coverage" })
    await remove.focus()
    await expect(remove).toBeFocused()
    await page.keyboard.press("Enter")
    await expect(page.getByRole("dialog", { name: "Delete session" })).toBeVisible()
  })

  test("exposes Local and Cloud as keyboard navigable selected tabs", async ({ page }) => {
    await story(page, "history-sessionlist--sources")

    const local = page.getByRole("tab", { name: "Local" })
    const cloud = page.getByRole("tab", { name: "Cloud" })
    await expect(page.getByRole("tablist", { name: "History source" })).toBeVisible()
    await expect(local).toHaveAttribute("aria-selected", "true")
    await expect(page.getByRole("tabpanel", { name: "Local" })).toBeVisible()

    await local.focus()
    await page.keyboard.press("ArrowRight")
    await expect(cloud).toBeFocused()
    await expect(local).toHaveAttribute("aria-selected", "true")
    await page.keyboard.press("Enter")
    await expect(cloud).toHaveAttribute("aria-selected", "true")
    await expect(page.getByRole("tabpanel", { name: "Cloud" })).toBeVisible()
    await expect(page.getByPlaceholder("Search sessions...")).toBeFocused()

    await cloud.focus()
    await page.keyboard.press("ArrowLeft")
    await expect(local).toBeFocused()
    await page.keyboard.press("Enter")
    await expect(local).toHaveAttribute("aria-selected", "true")
    await expect(page.getByRole("tabpanel", { name: "Local" })).toBeVisible()
  })
})
