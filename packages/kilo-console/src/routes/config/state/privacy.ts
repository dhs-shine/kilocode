import type { Model, Provider } from "@kilocode/sdk/v2/client"

export function hasGateway(providers: Pick<Provider, "id">[]) {
  return providers.some((provider) => provider.id === "kilo")
}

export function visible(model: Pick<Model, "mayTrainOnYourPrompts">, privacy: boolean) {
  return !privacy || model.mayTrainOnYourPrompts !== true
}
