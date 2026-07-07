import * as InstanceState from "@/effect/instance-state"
import { SessionStatus } from "@/session/status"
import { ConflictError } from "@/server/routes/instance/httpapi/errors"
import { markInstanceForReload } from "@/server/routes/instance/httpapi/lifecycle"
import { InstanceHttpApi } from "@/server/routes/instance/httpapi/api"
import { Effect } from "effect"
import { HttpApiBuilder } from "effect/unstable/httpapi"
import type { SessionID } from "@/session/schema"

export function hasBusySession(statuses: Map<SessionID, SessionStatus.Info>): boolean {
  for (const info of statuses.values()) {
    if (info.type === "busy") return true
  }
  return false
}

export const instanceReloadHandlers = HttpApiBuilder.group(InstanceHttpApi, "instance-reload", (handlers) =>
  Effect.gen(function* () {
    const status = yield* SessionStatus.Service

    const reload = Effect.fn("InstanceReloadHttpApi.reload")(function* () {
      const ctx = yield* InstanceState.context
      if (hasBusySession(yield* status.list())) {
        return yield* Effect.fail(
          new ConflictError({
            message: "Cannot reload while a session is running. Wait for it to finish or abort it first.",
          }),
        )
      }
      yield* markInstanceForReload(ctx, {
        directory: ctx.directory,
        worktree: ctx.worktree,
        project: ctx.project,
      })
      return true
    })

    return handlers.handle("reload", reload)
  }),
)
