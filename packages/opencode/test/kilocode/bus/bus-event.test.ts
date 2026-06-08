import { expect, test } from "bun:test"
import { Schema, SchemaAST } from "effect"
import { BusEvent } from "../../../src/bus/bus-event"

test("event payload schemas use stable event type order", () => {
  BusEvent.define("test.order.z", Schema.Struct({}))
  BusEvent.define("test.order.a", Schema.Struct({}))

  const ids = BusEvent.effectPayloads()
    .map((schema) => SchemaAST.resolveIdentifier(schema.ast))
    .filter((id) => id?.startsWith("Event.test.order."))

  expect(ids).toEqual(["Event.test.order.a", "Event.test.order.z"])
})
