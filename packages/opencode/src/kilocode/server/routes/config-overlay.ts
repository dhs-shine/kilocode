import { Hono } from "hono"
import { describeRoute, resolver, validator } from "hono-openapi"
import { Effect, Option } from "effect"
import { Account } from "@/account/account"
import { Auth } from "@/auth"
import { Config } from "@/config/config"
import { Instance } from "@/project/instance"
import { jsonRequest } from "@/server/routes/instance/trace"
import { lazy } from "@/util/lazy"
import { KilocodeConfigOverlay } from "@/kilocode/config/overlay"
import { KilocodeConfigSources } from "@/kilocode/config/sources"

export const ConfigOverlayRoutes = lazy(() =>
  new Hono()
    .get(
      "/overlay",
      describeRoute({
        summary: "Get config overlay",
        description:
          "Resolve global, project, and effective config values with source metadata for inheritance-aware settings UI.",
        operationId: "config.overlay",
        responses: {
          200: {
            description: "Resolved config overlay",
            content: {
              "application/json": {
                schema: resolver(KilocodeConfigOverlay.Result),
              },
            },
          },
        },
      }),
      validator("query", KilocodeConfigOverlay.Query),
      async (c) => {
        const query = c.req.valid("query")
        return jsonRequest("ConfigOverlayRoutes.overlay", c, function* () {
          const cfg = yield* Config.Service
          const auth = yield* Auth.Service
          const account = yield* Account.Service
          const all = yield* auth.all().pipe(Effect.orElseSucceed(() => ({})))
          const active = yield* account.active().pipe(
            Effect.map(Option.getOrUndefined),
            Effect.orElseSucceed(() => undefined),
          )
          const [base, global, sources] = yield* Effect.all(
            [
              cfg.get(),
              cfg.getGlobal(),
              Effect.promise(() =>
                KilocodeConfigSources.list({
                  directory: Instance.directory,
                  worktree: Instance.worktree,
                  auth: all,
                  account: active,
                }),
              ),
            ],
            { concurrency: 3 },
          )
          return yield* Effect.promise(() =>
            KilocodeConfigOverlay.resolve({
              directory: Instance.directory,
              worktree: Instance.worktree,
              scope: query.scope,
              effective: base,
              global,
              sources: sources.sources,
            }),
          )
        })
      },
    )
    .patch(
      "/overlay",
      describeRoute({
        summary: "Patch config overlay",
        description:
          "Apply a minimal global or project config patch, including unset paths for reverting local overrides.",
        operationId: "config.overlayUpdate",
        responses: {
          200: {
            description: "Effective configuration after patch",
            content: {
              "application/json": {
                schema: resolver(Config.Info.zod),
              },
            },
          },
        },
      }),
      validator("json", KilocodeConfigOverlay.Patch),
      async (c) =>
        jsonRequest("ConfigOverlayRoutes.overlay.update", c, function* () {
          const body = c.req.valid("json")
          const cfg = yield* Config.Service
          const patch = KilocodeConfigOverlay.patch(body)
          if (Object.keys(patch).length === 0) {
            if (body.scope === "global") return yield* cfg.getGlobal()
            return yield* cfg.get()
          }
          if (body.scope === "global") return yield* cfg.updateGlobal(patch)
          yield* cfg.update(patch)
          return yield* cfg.get()
        }),
    ),
)
