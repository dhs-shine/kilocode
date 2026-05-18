import { Schema } from "effect"
import { HttpApi, HttpApiEndpoint, HttpApiError, HttpApiGroup, OpenApi } from "effect/unstable/httpapi"
import { Authorization } from "@/server/routes/instance/httpapi/middleware/authorization"
import { InstanceContextMiddleware } from "@/server/routes/instance/httpapi/middleware/instance-context"
import { WorkspaceRoutingMiddleware } from "@/server/routes/instance/httpapi/middleware/workspace-routing"
import { described } from "@/server/routes/instance/httpapi/groups/metadata"

const root = "/kilo"

export const Organization = Schema.Struct({
  id: Schema.String,
  name: Schema.String,
  role: Schema.String,
})

export const Profile = Schema.Struct({
  email: Schema.String,
  name: Schema.optional(Schema.String),
  organizations: Schema.optional(Schema.Array(Organization)),
})

export const Balance = Schema.Struct({
  balance: Schema.Finite,
})

export const ProfileWithBalance = Schema.Struct({
  profile: Profile,
  balance: Schema.NullOr(Balance),
  currentOrgId: Schema.NullOr(Schema.String),
})

export const NotificationAction = Schema.Struct({
  actionText: Schema.String,
  actionURL: Schema.String,
})

export const Notification = Schema.Struct({
  id: Schema.String,
  title: Schema.String,
  message: Schema.String,
  action: Schema.optional(NotificationAction),
  showIn: Schema.optional(Schema.Array(Schema.String)),
  suggestModelId: Schema.optional(Schema.String),
})

export const OrganizationBody = Schema.Struct({
  organizationId: Schema.NullOr(Schema.String),
})

export const ClawStatus = Schema.Struct({
  status: Schema.NullOr(
    Schema.Literals([
      "provisioned",
      "starting",
      "restarting",
      "recovering",
      "running",
      "stopped",
      "destroying",
      "restoring",
    ]),
  ),
  sandboxId: Schema.optional(Schema.String),
  flyRegion: Schema.optional(Schema.String),
  machineSize: Schema.optional(
    Schema.Struct({
      cpus: Schema.Finite,
      memory_mb: Schema.Finite,
    }),
  ),
  openclawVersion: Schema.optional(Schema.NullOr(Schema.String)),
  lastStartedAt: Schema.optional(Schema.NullOr(Schema.String)),
  lastStoppedAt: Schema.optional(Schema.NullOr(Schema.String)),
  channelCount: Schema.optional(Schema.Finite),
  secretCount: Schema.optional(Schema.Finite),
  userId: Schema.optional(Schema.String),
  botName: Schema.optional(Schema.NullOr(Schema.String)),
})

export const ClawChatCredentials = Schema.NullOr(
  Schema.Struct({
    token: Schema.String,
    expiresAt: Schema.String,
    kiloChatUrl: Schema.String,
    eventServiceUrl: Schema.String,
  }),
)

export const CloudSession = Schema.Struct({
  session_id: Schema.String,
  title: Schema.NullOr(Schema.String),
  created_at: Schema.String,
  updated_at: Schema.String,
  version: Schema.Finite,
})

export const CloudSessions = Schema.Struct({
  cliSessions: Schema.Array(CloudSession),
  nextCursor: Schema.NullOr(Schema.String),
})

export const CloudSessionImportBody = Schema.Struct({
  sessionId: Schema.String,
})

export const CloudMessage = Schema.Struct({
  info: Schema.Struct({
    id: Schema.String,
    sessionID: Schema.String,
    role: Schema.Literals(["user", "assistant"]),
    time: Schema.Struct({
      created: Schema.Finite,
      completed: Schema.optional(Schema.Finite),
    }),
  }),
  parts: Schema.Array(
    Schema.Struct({
      id: Schema.String,
      sessionID: Schema.String,
      messageID: Schema.String,
      type: Schema.String,
    }),
  ),
})

export const CloudSessionData = Schema.Struct({
  info: Schema.Struct({
    id: Schema.String,
    title: Schema.String,
    time: Schema.Struct({
      created: Schema.Finite,
      updated: Schema.Finite,
    }),
  }),
  messages: Schema.Array(CloudMessage),
})

export const KiloGatewayPaths = {
  profile: `${root}/profile`,
  notifications: `${root}/notifications`,
  organization: `${root}/organization`,
  clawStatus: `${root}/claw/status`,
  clawChatCredentials: `${root}/claw/chat-credentials`,
  cloudSessions: `${root}/cloud-sessions`,
  cloudSession: `${root}/cloud/session/:id`,
  cloudSessionImport: `${root}/cloud/session/import`,
} as const

export const KiloGatewayApi = HttpApi.make("kilo")
  .add(
    HttpApiGroup.make("kilo")
      .add(
        HttpApiEndpoint.get("profile", KiloGatewayPaths.profile, {
          success: described(ProfileWithBalance, "Profile data"),
          error: [HttpApiError.BadRequest, HttpApiError.Unauthorized],
        }).annotateMerge(
          OpenApi.annotations({
            identifier: "kilo.profile",
            summary: "Get Kilo Gateway profile",
            description: "Fetch user profile and organizations from Kilo Gateway",
          }),
        ),
        HttpApiEndpoint.get("notifications", KiloGatewayPaths.notifications, {
          success: described(Schema.Array(Notification), "Notifications list"),
          error: [HttpApiError.BadRequest, HttpApiError.Unauthorized],
        }).annotateMerge(
          OpenApi.annotations({
            identifier: "kilo.notifications",
            summary: "Get Kilo notifications",
            description: "Fetch notifications from Kilo Gateway for CLI display",
          }),
        ),
        HttpApiEndpoint.post("organization", KiloGatewayPaths.organization, {
          payload: OrganizationBody,
          success: described(Schema.Boolean, "Organization updated successfully"),
          error: [HttpApiError.BadRequest, HttpApiError.Unauthorized],
        }).annotateMerge(
          OpenApi.annotations({
            identifier: "kilo.organization.set",
            summary: "Update Kilo Gateway organization",
            description: "Switch to a different Kilo Gateway organization",
          }),
        ),
        HttpApiEndpoint.get("clawStatus", KiloGatewayPaths.clawStatus, {
          success: described(ClawStatus, "Instance status"),
          error: [HttpApiError.Unauthorized, HttpApiError.ServiceUnavailable],
        }).annotateMerge(
          OpenApi.annotations({
            identifier: "kilo.claw.status",
            summary: "Get KiloClaw instance status",
            description: "Fetch the user's KiloClaw instance status via the KiloClaw worker",
          }),
        ),
        HttpApiEndpoint.get("clawChatCredentials", KiloGatewayPaths.clawChatCredentials, {
          success: described(ClawChatCredentials, "Kilo Chat credentials or null"),
          error: HttpApiError.Unauthorized,
        }).annotateMerge(
          OpenApi.annotations({
            identifier: "kilo.claw.chatCredentials",
            summary: "Get KiloClaw chat credentials",
            description:
              "Returns the bearer token and endpoint URLs the client uses to talk to the Kilo Chat worker " +
              "and the Event Service. The bearer is the user's existing long-lived Kilo JWT — kilo-chat and " +
              "event-service both verify it directly with NEXTAUTH_SECRET, so no separate token mint is needed.",
          }),
        ),
        HttpApiEndpoint.get("cloudSessions", KiloGatewayPaths.cloudSessions, {
          query: {
            cursor: Schema.optional(Schema.String),
            limit: Schema.optional(Schema.String),
            gitUrl: Schema.optional(Schema.String),
          },
          success: described(CloudSessions, "Cloud sessions list"),
          error: [HttpApiError.BadRequest, HttpApiError.Unauthorized],
        }).annotateMerge(
          OpenApi.annotations({
            identifier: "kilo.cloudSessions",
            summary: "Get cloud sessions",
            description: "Fetch cloud CLI sessions from Kilo API",
          }),
        ),
        HttpApiEndpoint.get("cloudSession", KiloGatewayPaths.cloudSession, {
          params: { id: Schema.String },
          success: described(CloudSessionData, "Cloud session data"),
          error: [HttpApiError.Unauthorized, HttpApiError.NotFound],
        }).annotateMerge(
          OpenApi.annotations({
            identifier: "kilo.cloud.session.get",
            summary: "Get cloud session",
            description: "Fetch full session data from the Kilo cloud for preview",
          }),
        ),
        HttpApiEndpoint.post("cloudSessionImport", KiloGatewayPaths.cloudSessionImport, {
          payload: CloudSessionImportBody,
          success: described(CloudSessionData.fields.info, "Imported session info"),
          error: [HttpApiError.BadRequest, HttpApiError.Unauthorized, HttpApiError.NotFound],
        }).annotateMerge(
          OpenApi.annotations({
            identifier: "kilo.cloud.session.import",
            summary: "Import session from cloud",
            description: "Download a cloud-synced session and write it to local storage with fresh IDs.",
          }),
        ),
      )
      .annotateMerge(
        OpenApi.annotations({
          title: "kilo",
          description: "Kilo Gateway routes.",
        }),
      )
      .middleware(InstanceContextMiddleware)
      .middleware(WorkspaceRoutingMiddleware)
      .middleware(Authorization),
  )
  .annotateMerge(
    OpenApi.annotations({
      title: "kilo HttpApi",
      version: "0.0.1",
      description: "Kilo HttpApi surface.",
    }),
  )
