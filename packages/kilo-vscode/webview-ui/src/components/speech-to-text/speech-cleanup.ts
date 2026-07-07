import type { SpeechToText } from "./useSpeechToText"

export function cleanupSpeechCapture(speech: Pick<SpeechToText, "active" | "cancel">) {
  if (speech.active()) speech.cancel()
}
