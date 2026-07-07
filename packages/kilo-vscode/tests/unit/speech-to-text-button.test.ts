import { describe, expect, it } from "bun:test"
import { cleanupSpeechCapture } from "../../webview-ui/src/components/speech-to-text/speech-cleanup"

describe("speech-to-text button", () => {
  it("cancels active capture on component cleanup", () => {
    let cancels = 0

    cleanupSpeechCapture({
      active: () => true,
      cancel: () => {
        cancels += 1
      },
    })

    expect(cancels).toBe(1)
  })

  it("does not cancel idle capture on component cleanup", () => {
    let cancels = 0

    cleanupSpeechCapture({
      active: () => false,
      cancel: () => {
        cancels += 1
      },
    })

    expect(cancels).toBe(0)
  })
})
