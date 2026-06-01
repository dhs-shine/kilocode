import { Hono } from "hono"
import { describeRoute, resolver, validator } from "hono-openapi"
import z from "zod"
import { lazy } from "@/util/lazy"
import { TuiConfig } from "@/cli/cmd/tui/config/tui"
import { Instance } from "@/project/instance"
import { errors } from "@/server/error"
import { KilocodeTuiConfig } from "@/kilocode/tui/config"
import { KilocodeKeybinds } from "@/kilocode/tui/keybinds"

const Query = z.object({
  scope: KilocodeTuiConfig.Scope.optional().default("project"),
})

export const TuiConfigRoutes = lazy(() =>
  new Hono()
    .get(
      "/config",
      describeRoute({
        summary: "Get TUI configuration",
        description: "Retrieve the effective TUI configuration for the current instance directory.",
        operationId: "tui.config.get",
        responses: {
          200: {
            description: "Effective TUI configuration",
            content: {
              "application/json": {
                schema: resolver(TuiConfig.Info),
              },
            },
          },
        },
      }),
      async (c) => {
        return c.json(await KilocodeTuiConfig.get({ directory: Instance.directory }))
      },
    )
    .patch(
      "/config",
      describeRoute({
        summary: "Update TUI configuration",
        description: "Patch global or project TUI configuration and return the effective TUI configuration.",
        operationId: "tui.config.update",
        responses: {
          200: {
            description: "Effective TUI configuration after the update",
            content: {
              "application/json": {
                schema: resolver(TuiConfig.Info),
              },
            },
          },
          ...errors(400),
        },
      }),
      validator("query", Query),
      validator("json", KilocodeTuiConfig.Patch),
      async (c) => {
        const query = c.req.valid("query")
        const patch = c.req.valid("json")
        return c.json(
          await KilocodeTuiConfig.update({
            directory: Instance.directory,
            worktree: Instance.worktree,
            scope: query.scope,
            patch,
          }),
        )
      },
    )
    .get(
      "/keybinds",
      describeRoute({
        summary: "List TUI keybinds",
        description:
          "List valid TUI keybind commands, descriptions, groups, and default bindings from the CLI schema.",
        operationId: "tui.keybind.list",
        responses: {
          200: {
            description: "TUI keybind metadata",
            content: {
              "application/json": {
                schema: resolver(
                  z.object({
                    keybinds: z.array(
                      z.object({
                        id: z.string(),
                        label: z.string(),
                        group: z.string(),
                        default: z.string(),
                        description: z.string(),
                      }),
                    ),
                  }),
                ),
              },
            },
          },
        },
      }),
      async (c) => c.json({ keybinds: KilocodeKeybinds.list() }),
    )
)
