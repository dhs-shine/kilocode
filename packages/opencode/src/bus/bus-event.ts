import { Schema } from "effect"

export type Definition<Type extends string = string, Properties extends Schema.Top = Schema.Top> = {
  type: Type
  properties: Properties
}

const registry = new Map<string, Definition>()

export function define<Type extends string, Properties extends Schema.Top>(
  type: Type,
  properties: Properties,
): Definition<Type, Properties> {
  const result = { type, properties }
  registry.set(type, result)
  return result
}

export function effectPayloads() {
  // kilocode_change start - keep generated schemas stable across module evaluation orders
  return registry
    .entries()
    .toArray()
    .sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0))
    .map(([type, def]) =>
      Schema.Struct({
        id: Schema.String,
        type: Schema.Literal(type),
        properties: def.properties,
      }).annotate({ identifier: `Event.${type}` }),
    )
  // kilocode_change end
}

export * as BusEvent from "./bus-event"
