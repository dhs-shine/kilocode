import type { Context } from "effect"
import type { Hono } from "hono"
import { AgentBuilderPaths } from "./groups/agent-builder"
import { BackgroundProcessPaths } from "./groups/background-process"
import { ConfigConsolePaths } from "./groups/config-console"
import { IndexingPaths } from "./groups/indexing"
import { KiloGatewayPaths } from "./groups/kilo-gateway"
import { KilocodePaths } from "./groups/kilocode"
import { NetworkPaths } from "./groups/network"
import { RemotePaths } from "./groups/remote"
import { SessionImportPaths } from "./groups/session-import"
import { SuggestionPaths } from "./groups/suggestion"
import { TelemetryPaths } from "./groups/telemetry"

type Handler = (request: Request, context: Context.Context<unknown>) => Promise<Response>

export function register(app: Hono, handler: Handler, context: Context.Context<unknown>) {
  app.post(AgentBuilderPaths.preview, (c) => handler(c.req.raw, context))
  app.put(AgentBuilderPaths.save, (c) => handler(c.req.raw, context))
  app.get(BackgroundProcessPaths.list, (c) => handler(c.req.raw, context))
  app.get(BackgroundProcessPaths.get, (c) => handler(c.req.raw, context))
  app.get(BackgroundProcessPaths.logs, (c) => handler(c.req.raw, context))
  app.post(BackgroundProcessPaths.stop, (c) => handler(c.req.raw, context))
  app.post(BackgroundProcessPaths.restart, (c) => handler(c.req.raw, context))
  app.post(BackgroundProcessPaths.stopSession, (c) => handler(c.req.raw, context))
  app.get(ConfigConsolePaths.sources, (c) => handler(c.req.raw, context))
  app.get(ConfigConsolePaths.effective, (c) => handler(c.req.raw, context))
  app.get(ConfigConsolePaths.overlay, (c) => handler(c.req.raw, context))
  app.patch(ConfigConsolePaths.overlay, (c) => handler(c.req.raw, context))
  app.get(ConfigConsolePaths.rules, (c) => handler(c.req.raw, context))
  app.put(ConfigConsolePaths.rules, (c) => handler(c.req.raw, context))
  app.get(ConfigConsolePaths.modelState, (c) => handler(c.req.raw, context))
  app.patch(ConfigConsolePaths.modelState, (c) => handler(c.req.raw, context))
  app.get(ConfigConsolePaths.tuiConfig, (c) => handler(c.req.raw, context))
  app.get(ConfigConsolePaths.tuiKeybinds, (c) => handler(c.req.raw, context))
  app.patch(ConfigConsolePaths.tuiConfig, (c) => handler(c.req.raw, context))
  app.post("/permission/allow-everything", (c) => handler(c.req.raw, context))
  app.post("/enhance-prompt", (c) => handler(c.req.raw, context))
  app.post("/commit-message", (c) => handler(c.req.raw, context))
  app.get(NetworkPaths.list, (c) => handler(c.req.raw, context))
  app.post(NetworkPaths.reply, (c) => handler(c.req.raw, context))
  app.post(NetworkPaths.reject, (c) => handler(c.req.raw, context))
  app.post(RemotePaths.enable, (c) => handler(c.req.raw, context))
  app.post(RemotePaths.disable, (c) => handler(c.req.raw, context))
  app.get(RemotePaths.status, (c) => handler(c.req.raw, context))
  app.post(TelemetryPaths.capture, (c) => handler(c.req.raw, context))
  app.post(TelemetryPaths.setEnabled, (c) => handler(c.req.raw, context))
  app.get(SuggestionPaths.list, (c) => handler(c.req.raw, context))
  app.post(SuggestionPaths.accept, (c) => handler(c.req.raw, context))
  app.post(SuggestionPaths.dismiss, (c) => handler(c.req.raw, context))
  app.post(KilocodePaths.heapSnapshot, (c) => handler(c.req.raw, context))
  app.post(KilocodePaths.removeSkill, (c) => handler(c.req.raw, context))
  app.post(KilocodePaths.removeAgent, (c) => handler(c.req.raw, context))
  app.post(SessionImportPaths.project, (c) => handler(c.req.raw, context))
  app.post(SessionImportPaths.session, (c) => handler(c.req.raw, context))
  app.post(SessionImportPaths.message, (c) => handler(c.req.raw, context))
  app.post(SessionImportPaths.part, (c) => handler(c.req.raw, context))
  app.get(IndexingPaths.status, (c) => handler(c.req.raw, context))
  app.get(KiloGatewayPaths.profile, (c) => handler(c.req.raw, context))
  app.get(KiloGatewayPaths.modes, (c) => handler(c.req.raw, context))
  app.post(KiloGatewayPaths.fim, (c) => handler(c.req.raw, context))
  app.post(KiloGatewayPaths.audioTranscriptions, (c) => handler(c.req.raw, context))
  app.get(KiloGatewayPaths.notifications, (c) => handler(c.req.raw, context))
  app.post(KiloGatewayPaths.organization, (c) => handler(c.req.raw, context))
  app.get(KiloGatewayPaths.clawStatus, (c) => handler(c.req.raw, context))
  app.get(KiloGatewayPaths.clawChatCredentials, (c) => handler(c.req.raw, context))
  app.get(KiloGatewayPaths.cloudSessions, (c) => handler(c.req.raw, context))
  app.get(KiloGatewayPaths.cloudSession, (c) => handler(c.req.raw, context))
  app.post(KiloGatewayPaths.cloudSessionImport, (c) => handler(c.req.raw, context))

  return app
}
