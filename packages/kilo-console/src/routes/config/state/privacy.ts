import type { Model } from "@kilocode/sdk/v2/client"

export function visible(model: Pick<Model, "mayTrainOnYourPrompts">, privacy: boolean) {
  return !privacy || model.mayTrainOnYourPrompts !== true
}
