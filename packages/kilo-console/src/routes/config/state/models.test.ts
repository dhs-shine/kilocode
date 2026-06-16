import { describe, expect, test } from "bun:test"
import { visible } from "./privacy"

describe("model privacy filter", () => {
  test("shows every model when disabled", () => {
    expect(visible({ mayTrainOnYourPrompts: true }, false)).toBe(true)
  })

  test("hides only models explicitly marked for prompt training", () => {
    expect(visible({ mayTrainOnYourPrompts: true }, true)).toBe(false)
    expect(visible({ mayTrainOnYourPrompts: false }, true)).toBe(true)
    expect(visible({}, true)).toBe(true)
  })
})
