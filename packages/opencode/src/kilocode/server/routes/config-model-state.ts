import { Hono } from "hono"
import { describeRoute, resolver, validator } from "hono-openapi"
import { lazy } from "@/util/lazy"
import { KilocodeModelState } from "@/kilocode/config/model-state"

export const ConfigModelStateRoutes = lazy(() =>
  new Hono()
    .get(
      "/model-state",
      describeRoute({
        summary: "Get model state",
        description: "Retrieve TUI-compatible recent and favorite model selections.",
        operationId: "config.modelState",
        responses: {
          200: {
            description: "Model state",
            content: {
              "application/json": {
                schema: resolver(KilocodeModelState.State),
              },
            },
          },
        },
      }),
      async (c) => c.json(await KilocodeModelState.get()),
    )
    .patch(
      "/model-state",
      describeRoute({
        summary: "Update model state",
        description: "Patch TUI-compatible model selections shared with Kilo Console.",
        operationId: "config.modelStateUpdate",
        responses: {
          200: {
            description: "Updated model state",
            content: {
              "application/json": {
                schema: resolver(KilocodeModelState.State),
              },
            },
          },
        },
      }),
      validator("json", KilocodeModelState.Patch),
      async (c) => c.json(await KilocodeModelState.update(c.req.valid("json"))),
    ),
)
