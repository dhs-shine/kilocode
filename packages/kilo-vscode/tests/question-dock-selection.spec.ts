import { expect, test, type Page } from "@playwright/test"

const GLOBALS = "colorScheme:dark;theme:kilo-vscode;vscodeTheme:dark-modern"

async function load(page: Page) {
  await page.goto(`/iframe.html?id=chat--question-dock-single&viewMode=story&globals=${GLOBALS}`, {
    waitUntil: "load",
  })
  await page.waitForSelector("#storybook-root *", { state: "attached" })
}

test("custom answer selection follows the custom row, input, and predefined options", async ({ page }) => {
  await load(page)

  const options = page.locator('button[data-slot="question-option"]')
  const vitest = options.filter({ hasText: "Vitest" })
  const jest = options.filter({ hasText: "Jest" })
  const custom = options.filter({ hasText: "Type your own answer" })

  await vitest.click()
  await expect(vitest).toHaveAttribute("data-picked", "true")

  await custom.click()
  const input = page.locator('input[data-slot="custom-input"]')
  await expect(input).toBeFocused()
  await expect(custom).toHaveAttribute("data-picked", "true")
  await expect(vitest).toHaveAttribute("data-picked", "false")

  await input.fill("Custom runner")
  await jest.click()
  await expect(jest).toHaveAttribute("data-picked", "true")
  await expect(custom).toHaveAttribute("data-picked", "false")
  await expect(input).toHaveCount(0)

  await page.locator('[data-slot="question-dock-footer"]').getByRole("button", { name: "Submit" }).click()
  await expect(page.getByTestId("question-reply")).toHaveText(
    JSON.stringify({ id: "q-single-001", answers: [["Jest"]] }),
  )
})
