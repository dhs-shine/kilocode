import { Effect } from "effect"
import { HttpApiBuilder } from "effect/unstable/httpapi"
import { InstanceHttpApi } from "@/server/routes/instance/httpapi/api"
import { enhancePrompt } from "@/kilocode/enhance-prompt"
import { EnhancePromptPayload } from "../groups/enhance-prompt"

export const enhancePromptHandlers = HttpApiBuilder.group(InstanceHttpApi, "enhance-prompt", (handlers) =>
  Effect.gen(function* () {
    const enhance = Effect.fn("EnhancePromptHttpApi.enhance")(function* (ctx: {
      payload: typeof EnhancePromptPayload.Type
    }) {
      const text = yield* Effect.promise(() => enhancePrompt(ctx.payload.text))
      return { text }
    })

    return handlers.handle("enhance", enhance)
  }),
)
