export const FreeModelDisclosure = {
  label: "May train",
  panel: "Free - may train",
  collectsData(model: { isFree?: boolean; api?: { npm?: string } }): boolean {
    return model.isFree === true && model.api?.npm === "@kilocode/kilo-gateway"
  },
} as const
