import { Effect } from "effect"
import { HttpApiBuilder } from "effect/unstable/httpapi"
import * as SandboxPolicy from "@/kilocode/sandbox/policy"
import { Session } from "@/session/session"
import type { SessionID } from "@/session/schema"
import { InstanceHttpApi } from "@/server/routes/instance/httpapi/api"
import * as SessionError from "@/server/routes/instance/httpapi/handlers/session-errors"
import { BackgroundProcess } from "@/kilocode/background-process"
import { InteractiveTerminal } from "@/kilocode/interactive-terminal"
import { Service as Notebook } from "@/kilocode/notebook/service"
import { SessionStatus } from "@/session/status"
import { InvalidRequestError } from "@/server/routes/instance/httpapi/errors"

export const sandboxHandlers = HttpApiBuilder.group(InstanceHttpApi, "sandbox", (handlers) =>
  Effect.gen(function* () {
    const session = yield* Session.Service
    const notebook = yield* Notebook
    const status = yield* SessionStatus.Service
    const exists = (sessionID: SessionID) => SessionError.mapStorageNotFound(session.get(sessionID))
    return handlers
      .handle("support", () => SandboxPolicy.configuredSupport())
      .handle("status", (ctx: { params: { sessionID: SessionID } }) =>
        exists(ctx.params.sessionID).pipe(Effect.andThen(SandboxPolicy.status(ctx.params.sessionID))),
      )
      .handle("toggle", (ctx: { params: { sessionID: SessionID } }) =>
        SandboxPolicy.toggleGuarded(ctx.params.sessionID, (enabling) =>
          exists(ctx.params.sessionID).pipe(
            Effect.andThen(
              enabling
                ? Effect.gen(function* () {
                    if ((yield* status.get(ctx.params.sessionID)).type !== "idle") {
                      return yield* new InvalidRequestError({
                        message: "Stop the active session before enabling sandbox confinement",
                      })
                    }
                    yield* Effect.all(
                      [
                        Effect.promise(() => BackgroundProcess.stopSession(ctx.params.sessionID)),
                        Effect.promise(() => InteractiveTerminal.stopSession(ctx.params.sessionID)),
                        notebook.cancelSession(ctx.params.sessionID),
                      ],
                      { discard: true },
                    )
                  })
                : Effect.void,
            ),
          ),
        ),
      )
  }),
)
