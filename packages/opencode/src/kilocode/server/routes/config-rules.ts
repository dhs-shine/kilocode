import path from "path"
import { Hono } from "hono"
import { describeRoute, resolver, validator } from "hono-openapi"
import z from "zod"
import { Instance } from "@/project/instance"
import { Filesystem } from "@/util/filesystem"
import { lazy } from "@/util/lazy"

export namespace ConfigRulesRouteSchema {
  export const Query = z.object({
    scope: z.literal("project").default("project"),
  })

  export const File = z.object({
    name: z.string(),
    path: z.string(),
    exists: z.boolean(),
    editable: z.boolean(),
    content: z.string(),
  })

  export const Result = z.object({
    scope: z.literal("project"),
    target: z.string(),
    files: z.array(File),
  })

  export const Update = z.object({
    scope: z.literal("project").default("project"),
    content: z.string(),
  })
}

export namespace ConfigRules {
  const names = ["AGENTS.md", "CLAUDE.md", "CONTEXT.md"] as const

  type Ctx = {
    directory: string
    worktree?: string
  }

  function root(ctx?: Ctx) {
    if (ctx) return ctx.worktree && ctx.worktree !== "/" ? ctx.worktree : ctx.directory
    if (Instance.worktree && Instance.worktree !== "/") return Instance.worktree
    return Instance.directory
  }

  function target(ctx?: Ctx) {
    return path.join(root(ctx), "AGENTS.md")
  }

  export async function read(ctx?: Ctx) {
    const dir = root(ctx)
    const files = await Promise.all(
      names.map(async (name) => {
        const file = path.join(dir, name)
        const exists = await Bun.file(file).exists()
        return {
          name,
          path: file,
          exists,
          editable: name === "AGENTS.md",
          content: exists ? await Bun.file(file).text() : "",
        }
      }),
    )
    return {
      scope: "project" as const,
      target: target(ctx),
      files,
    }
  }

  export async function update(input: Ctx & { content: string }) {
    await Filesystem.write(target(input), input.content)
    return read(input)
  }
}

export const ConfigRulesRoutes = lazy(() =>
  new Hono()
    .get(
      "/rules",
      describeRoute({
        summary: "Get project rules",
        description: "List project instruction files used by Kilo and return their current contents.",
        operationId: "config.rules",
        responses: {
          200: {
            description: "Project rules",
            content: {
              "application/json": {
                schema: resolver(ConfigRulesRouteSchema.Result),
              },
            },
          },
        },
      }),
      validator("query", ConfigRulesRouteSchema.Query),
      async (c) => c.json(await ConfigRules.read()),
    )
    .put(
      "/rules",
      describeRoute({
        summary: "Update project rules",
        description: "Create or update the project AGENTS.md rules file.",
        operationId: "config.rulesUpdate",
        responses: {
          200: {
            description: "Project rules after update",
            content: {
              "application/json": {
                schema: resolver(ConfigRulesRouteSchema.Result),
              },
            },
          },
        },
      }),
      validator("json", ConfigRulesRouteSchema.Update),
      async (c) => {
        const body = c.req.valid("json")
        return c.json(
          await ConfigRules.update({ directory: Instance.directory, worktree: Instance.worktree, content: body.content }),
        )
      },
    ),
)
