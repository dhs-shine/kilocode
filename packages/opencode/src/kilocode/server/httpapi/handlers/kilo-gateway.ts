import { GatewayError, UnauthorizedError, getOrganizationId, getToken } from "@kilocode/kilo-gateway"
import {
  HEADER_ORGANIZATIONID,
  KILO_API_BASE,
  KILO_CHAT_URL,
  KILO_EVENT_SERVICE_URL,
  clearModesCache,
  fetchBalance,
  fetchKilocodeNotifications,
  fetchProfile,
} from "@kilocode/kilo-gateway"
import { Effect } from "effect"
import { HttpApiBuilder, HttpApiError } from "effect/unstable/httpapi"
import { Auth } from "@/auth"
import { InstanceStore } from "@/project/instance-store"
import { ModelCache } from "@/provider/model-cache"
import { InstanceHttpApi } from "@/server/routes/instance/httpapi/api"

export const kiloGatewayHandlers = HttpApiBuilder.group(InstanceHttpApi, "kilo", (handlers) =>
  Effect.gen(function* () {
    const auth = yield* Auth.Service
    const store = yield* InstanceStore.Service

    const profile = Effect.fn("KiloGatewayHttpApi.profile")(function* () {
      const info = yield* auth.get("kilo").pipe(Effect.mapError(() => new HttpApiError.BadRequest({})))
      if (!info || info.type !== "oauth") return yield* Effect.fail(new HttpApiError.Unauthorized({}))

      const currentOrgId = info.accountId ?? null
      const [profile, balance] = yield* Effect.tryPromise({
        try: () => Promise.all([fetchProfile(info.access), fetchBalance(info.access, currentOrgId ?? undefined)]),
        catch: () => new HttpApiError.BadRequest({}),
      })
      return { profile, balance, currentOrgId }
    })

    const notifications = Effect.fn("KiloGatewayHttpApi.notifications")(function* () {
      const info = yield* auth.get("kilo").pipe(Effect.mapError(() => new HttpApiError.BadRequest({})))
      const token = getToken(info)
      if (!token) return []

      return yield* Effect.promise(() =>
        fetchKilocodeNotifications({
          kilocodeToken: token,
          kilocodeOrganizationId: getOrganizationId(info),
        }),
      )
    })

    const organization = Effect.fn("KiloGatewayHttpApi.organization")(function* (ctx) {
      const info = yield* auth.get("kilo").pipe(Effect.mapError(() => new HttpApiError.Unauthorized({})))
      if (!info || info.type !== "oauth") return yield* Effect.fail(new HttpApiError.Unauthorized({}))

      yield* auth
        .set("kilo", {
          type: "oauth",
          refresh: info.refresh,
          access: info.access,
          expires: info.expires,
          ...(ctx.payload.organizationId && { accountId: ctx.payload.organizationId }),
        })
        .pipe(Effect.mapError(() => new HttpApiError.Unauthorized({})))

      ModelCache.clear("kilo")
      clearModesCache()
      yield* store.disposeAll().pipe(Effect.mapError(() => new HttpApiError.Unauthorized({})))
      return true
    })

    const clawStatus = Effect.fn("KiloGatewayHttpApi.clawStatus")(function* () {
      const info = yield* auth.get("kilo").pipe(Effect.mapError(() => new HttpApiError.ServiceUnavailable({})))
      const token = getToken(info)
      if (!token) return yield* Effect.fail(new HttpApiError.Unauthorized({}))

      const headers: Record<string, string> = {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      }
      const org = getOrganizationId(info)
      if (org) headers[HEADER_ORGANIZATIONID] = org

      return yield* Effect.tryPromise({
        try: async () => {
          const response = await fetch(`${KILO_API_BASE}/api/kiloclaw/status`, { headers })
          if (!response.ok) throw new GatewayError(await response.text(), response.status)
          return response.json()
        },
        catch: (err) =>
          err instanceof UnauthorizedError
            ? new HttpApiError.Unauthorized({})
            : new HttpApiError.ServiceUnavailable({}),
      })
    })

    const clawChatCredentials = Effect.fn("KiloGatewayHttpApi.clawChatCredentials")(function* () {
      const info = yield* auth.get("kilo").pipe(Effect.mapError(() => new HttpApiError.Unauthorized({})))
      const token = getToken(info)
      if (!token) return yield* Effect.fail(new HttpApiError.Unauthorized({}))

      const expires = info?.type === "oauth" ? info.expires : Date.now() + 365 * 24 * 60 * 60 * 1000
      return {
        token,
        expiresAt: new Date(expires).toISOString(),
        kiloChatUrl: KILO_CHAT_URL,
        eventServiceUrl: KILO_EVENT_SERVICE_URL,
      }
    })

    return handlers
      .handle("profile", profile)
      .handle("notifications", notifications)
      .handle("organization", organization)
      .handle("clawStatus", clawStatus)
      .handle("clawChatCredentials", clawChatCredentials)
  }),
)
