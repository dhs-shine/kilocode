import { Hono } from "hono"
import { describeRoute, resolver } from "hono-openapi"
import { Effect, Option } from "effect"
import { Account } from "@/account/account"
import { Auth } from "@/auth"
import { Config } from "@/config/config"
import { Instance } from "@/project/instance"
import { jsonRequest } from "@/server/routes/instance/trace"
import { lazy } from "@/util/lazy"
import { KilocodeConfigSources } from "@/kilocode/config/sources"

export const ConfigSourcesRoutes = lazy(() =>
  new Hono()
    .get(
      "/sources",
      describeRoute({
        summary: "List config sources",
        description: "List config source metadata in load order without exposing config contents or secrets.",
        operationId: "config.sources",
        responses: {
          200: {
            description: "Config source inventory",
            content: {
              "application/json": {
                schema: resolver(KilocodeConfigSources.Result),
              },
            },
          },
        },
      }),
      async (c) =>
        jsonRequest("ConfigSourcesRoutes.sources", c, function* () {
          const auth = yield* Auth.Service
          const account = yield* Account.Service
          const all = yield* auth.all().pipe(Effect.orElseSucceed(() => ({})))
          const active = yield* account.active().pipe(
            Effect.map(Option.getOrUndefined),
            Effect.orElseSucceed(() => undefined),
          )
          return yield* Effect.promise(() =>
            KilocodeConfigSources.list({
              directory: Instance.directory,
              worktree: Instance.worktree,
              auth: all,
              account: active,
            }),
          )
        }),
    )
    .get(
      "/effective",
      describeRoute({
        summary: "Get effective configuration",
        description: "Retrieve effective config for the current instance directory.",
        operationId: "config.effective",
        responses: {
          200: {
            description: "Effective config info",
            content: {
              "application/json": {
                schema: resolver(Config.Info.zod),
              },
            },
          },
        },
      }),
      async (c) =>
        jsonRequest("ConfigSourcesRoutes.effective", c, function* () {
          const cfg = yield* Config.Service
          return yield* cfg.get()
        }),
    ),
)
