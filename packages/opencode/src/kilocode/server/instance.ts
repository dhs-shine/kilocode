// Registers all Kilo-specific instance routes on a Hono app.
// Called from ../../server/instance/index.ts before the UI fallback route.

import { Hono } from "hono"
import { describeRoute, validator, resolver } from "hono-openapi"
import z from "zod"
import { TelemetryRoutes } from "../../server/routes/instance/telemetry"
import { CommitMessageRoutes } from "./routes/commit-message"
import { EnhancePromptRoutes } from "../../server/routes/instance/enhance-prompt"
import { KilocodeRoutes } from "../../server/routes/instance/kilocode"
import { PermissionKilocodeRoutes } from "../permission/routes"
import { RemoteRoutes } from "../../server/routes/instance/remote"
import { NetworkRoutes } from "../../server/routes/instance/network"
import { SuggestionRoutes } from "../suggestion/routes"
import { ConfigSourcesRoutes } from "./routes/config-sources"
import { ConfigOverlayRoutes } from "./routes/config-overlay"
import { ConfigRulesRoutes } from "./routes/config-rules"
import { ConfigModelStateRoutes } from "./routes/config-model-state"
import { AgentBuilderRoutes } from "./routes/agent-builder"
import { IndexingRoutes } from "./routes/indexing"
import { TuiConfigRoutes } from "./routes/tui-config"
import { createKiloRoutes } from "@kilocode/kilo-gateway"
import { Auth } from "../../auth"
import { errors } from "../../server/error"
import { ModelCache } from "../../provider/model-cache"
import { Database } from "../../storage/db"
import { Instance } from "../../project/instance"
import { InstanceStore } from "../../project/instance-store"
import { Session } from "../../session/session"
import { Identifier } from "../../id/id"
import { SessionTable, MessageTable, PartTable } from "../../session/session.sql"
import { Bus } from "@/bus"

export function register(app: Hono): Hono {
  return app
    .route("/permission", PermissionKilocodeRoutes())
    .route("/agent-builder", AgentBuilderRoutes())
    .route("/network", NetworkRoutes())
    .route("/indexing", IndexingRoutes())
    .route("/suggestion", SuggestionRoutes())
    .route("/config", ConfigSourcesRoutes())
    .route("/config", ConfigOverlayRoutes())
    .route("/config", ConfigRulesRoutes())
    .route("/config", ConfigModelStateRoutes())
    .route("/telemetry", TelemetryRoutes())
    .route("/remote", RemoteRoutes())
    .route("/commit-message", CommitMessageRoutes())
    .route("/enhance-prompt", EnhancePromptRoutes())
    .route("/tui", TuiConfigRoutes())
    .route("/kilocode", KilocodeRoutes())
    .route(
      "/kilo",
      createKiloRoutes({
        Hono,
        describeRoute,
        validator,
        resolver,
        errors,
        Auth,
        z,
        Database,
        Instance,
        InstanceStore,
        SessionTable,
        MessageTable,
        PartTable,
        SessionToRow: Session.toRow,
        Bus,
        SessionCreatedEvent: Session.Event.Created,
        Identifier,
        ModelCache,
      }),
    )
}
