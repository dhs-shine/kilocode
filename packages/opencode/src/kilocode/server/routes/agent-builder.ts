import { Hono } from "hono"
import { describeRoute, resolver, validator } from "hono-openapi"
import { AgentBuilder } from "@/kilocode/agent/builder"
import { AppRuntime } from "@/effect/app-runtime"
import { Instance } from "@/project/instance"
import { InstanceStore } from "@/project/instance-store"
import { errors } from "@/server/error"
import { lazy } from "@/util/lazy"

export const AgentBuilderRoutes = lazy(() =>
  new Hono()
    .post(
      "/preview",
      describeRoute({
        summary: "Preview agent markdown",
        description: "Validate an agent builder payload and return the canonical agent markdown without writing it.",
        operationId: "agentBuilder.preview",
        responses: {
          200: {
            description: "Agent markdown preview",
            content: {
              "application/json": {
                schema: resolver(AgentBuilder.Output),
              },
            },
          },
          ...errors(400),
        },
      }),
      validator("json", AgentBuilder.Input),
      async (c) => c.json(await AgentBuilder.preview(context(), c.req.valid("json"))),
    )
    .put(
      "/:id",
      describeRoute({
        summary: "Save agent markdown",
        description: "Save an agent builder payload as a canonical agent markdown file.",
        operationId: "agentBuilder.save",
        responses: {
          200: {
            description: "Saved agent markdown",
            content: {
              "application/json": {
                schema: resolver(AgentBuilder.Output),
              },
            },
          },
          ...errors(400),
        },
      }),
      validator("param", AgentBuilder.Params),
      validator("json", AgentBuilder.SaveInput),
      async (c) => {
        const body = c.req.valid("json")
        const input = { ...body, id: c.req.valid("param").id }
        const output = await AgentBuilder.save(context(), input)
        await AppRuntime.runPromise(InstanceStore.Service.use((svc) => svc.dispose(Instance.current)))
        return c.json(output)
      },
    ),
)

function context() {
  return { directory: Instance.directory, worktree: Instance.worktree }
}
