import { Effect } from "effect"
import { HttpApiBuilder } from "effect/unstable/httpapi"
import { QuestionID } from "@/question/schema"
import { SessionNetwork } from "@/session/network"
import { InstanceHttpApi } from "@/server/routes/instance/httpapi/api"

export const networkHandlers = HttpApiBuilder.group(InstanceHttpApi, "network", (handlers) =>
  Effect.gen(function* () {
    const list = Effect.fn("NetworkHttpApi.list")(function* () {
      return yield* Effect.promise(() => SessionNetwork.list())
    })

    const reply = Effect.fn("NetworkHttpApi.reply")(function* (ctx: { params: { requestID: QuestionID } }) {
      yield* Effect.promise(() => SessionNetwork.reply({ requestID: ctx.params.requestID }))
      return true
    })

    const reject = Effect.fn("NetworkHttpApi.reject")(function* (ctx: { params: { requestID: QuestionID } }) {
      yield* Effect.promise(() => SessionNetwork.reject({ requestID: ctx.params.requestID }))
      return true
    })

    return handlers.handle("list", list).handle("reply", reply).handle("reject", reject)
  }),
)
