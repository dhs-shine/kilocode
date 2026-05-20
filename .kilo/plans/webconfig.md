# Web Config Dashboard Plan

## Goal

Build a daemon-backed Kilo configuration dashboard that becomes the advanced replacement for JSON-based CLI configuration and eventually supersedes the VS Code settings panel.

The core product shape:

- `kilo` starts or attaches to a local background daemon.
- The daemon owns the HTTP/SSE API and serves a local web dashboard.
- The dashboard configures global, project, and profile-scoped Kilo behavior.
- The UI uses `@kilocode/kilo-ui` with a standalone SolidJS app.
- Existing config files remain the canonical storage format, but users mostly interact visually.

## Key Findings

Current CLI config already supports most primitives, but not the product model:

- Main CLI config is in `packages/opencode/src/config/config.ts`.
- Project/global updates already exist through `Config.update()` and `Config.updateGlobal()`.
- Existing APIs expose resolved config, but not enough provenance/source information for a serious UI.
- TUI config is separate in `tui.json[c]`, not `kilo.json[c]`.
- Existing VS Code settings UI is SolidJS and uses `kilo-ui`, but it is tightly coupled to VS Code webview messaging and VS Code settings storage.
- Current default TUI and `kilo run` do not use a persistent server; they use in-process or worker-local server transports.
- `kilo serve` already exposes HTTP/SSE and can serve static UI assets, but it is foreground and unauthenticated unless `KILO_SERVER_PASSWORD` is set.

## Implementation Checklist

This plan will be implemented one checkpoint at a time. Each checkpoint should leave the repo in a manually testable state before moving to the next one.

Manual tests use port `4097` because `4096` is often occupied by the VS Code extension's background `kilo serve --port 0` process. If `4097` is also occupied, use another free localhost port and replace the port in the commands below.

### Checkpoint 0: Existing Server Probe

Status: Complete before this plan started.

- [x] Confirm the server already exposes `GET /global/health` for daemon attach/status probing.
- [x] Treat this as the base health contract for future daemon status checks.

Manual test:

```bash
bun run --conditions=browser ./src/index.ts serve --hostname 127.0.0.1 --port 4097
curl http://127.0.0.1:4097/global/health
```

Expected result:

```json
{"healthy":true,"version":"<current version>"}
```

### Checkpoint 1: TUI Config HTTP API

Status: Complete.

- [x] Add Kilo-owned helpers to read effective TUI config for a requested instance directory.
- [x] Add Kilo-owned helpers to patch global or project `tui.json[c]` with sparse updates.
- [x] Add `GET /tui/config` for the effective TUI config.
- [x] Add `PATCH /tui/config?scope=project|global` for TUI config writes.
- [x] Add focused tests for reading and updating project TUI config.
- [x] Regenerate SDK output after adding the server endpoint.

Manual test:

```bash
bun run --conditions=browser ./src/index.ts serve --hostname 127.0.0.1 --port 4097
curl -H "x-kilo-directory: $PWD" http://127.0.0.1:4097/tui/config
curl -X PATCH -H "content-type: application/json" -H "x-kilo-directory: $PWD" "http://127.0.0.1:4097/tui/config?scope=project" --data '{"theme":"dracula"}'
```

Expected result:

- The first request returns effective TUI settings.
- The patch request creates or updates `.kilo/tui.json` for the project.
- A follow-up `GET /tui/config` includes `"theme":"dracula"`.

### Checkpoint 2: Config Source Inventory API

Status: Complete.

- [x] Add read-only config source inventory for global, project, config-dir, env, managed, and cloud sources.
- [x] Expose source path, scope, existence, editability, and precedence metadata.
- [x] Do not expose secrets from provider options or auth storage.
- [x] Add tests for source ordering and project directory behavior.

Manual test:

```bash
curl -H "x-kilo-directory: $PWD" http://127.0.0.1:4097/config/sources
```

Expected result:

- The response lists discovered config files/directories in precedence order.
- Read-only or managed sources are marked non-editable.

### Checkpoint 3: Profile Storage API

Status: Complete.

- [x] Define profile metadata schemas.
- [x] Add global profile list/create/update/delete endpoints.
- [x] Add project profile list/create/update/delete endpoints.
- [x] Add active profile selection metadata without changing runtime config precedence yet.
- [x] Add tests for profile file creation and validation.

Manual test:

```bash
TMPDIR=$(mktemp -d)
curl -H "x-kilo-directory: $TMPDIR" http://127.0.0.1:4097/profiles
curl -X POST -H "content-type: application/json" -H "x-kilo-directory: $TMPDIR" http://127.0.0.1:4097/profiles --data '{"scope":"project","id":"work","name":"Work"}'
curl -X POST -H "x-kilo-directory: $TMPDIR" "http://127.0.0.1:4097/profiles/work/activate?scope=project"
cat "$TMPDIR/.kilo/profiles/index.jsonc"
```

Expected result:

- A new project profile appears in the profile list.
- Profile metadata is persisted under `$TMPDIR/.kilo/profiles/index.jsonc`.
- Empty `$TMPDIR/.kilo/profiles/work/kilo.jsonc` and `tui.jsonc` files are created.

### Checkpoint 4: Profile Overlay Runtime

Status: Complete.

- [x] Load active global profile overlays after global base config.
- [x] Load active project profile overlays after project base config.
- [x] Keep env, cloud, managed, and runtime overlays at their current special precedence.
- [x] Add tests for effective config with global and project profile overlays.

Manual test:

```bash
curl -H "x-kilo-directory: $PWD" "http://127.0.0.1:4097/config/effective?profile=work"
```

Expected result:

- Effective config includes profile values in the documented precedence order.
- Base project config still overrides global base config.

### Checkpoint 5: Dashboard Scaffold

Status: Complete.

- [x] Create `packages/kilo-config-ui` as a SolidJS/Vite app.
- [x] Reuse `@kilocode/kilo-ui` and the standalone `kilo` theme.
- [x] Add SDK/HTTP client bootstrap against the local daemon/server.
- [x] Add dashboard shell, diagnostics, scope selector, and read-only config summary.

Manual test:

```bash
# Terminal 1
cd packages/opencode
bun run --conditions=browser ./src/index.ts serve --hostname 127.0.0.1 --port 4097

# Terminal 2
cd packages/kilo-config-ui
bun run dev
open "http://127.0.0.1:3017?server=http://127.0.0.1:4097&directory=$PWD"
```

Expected result:

- The dashboard loads in a browser.
- It can show server health and effective config for the selected directory.

### Checkpoint 6: Providers And Models UI

Status: Complete.

- [x] Add provider list and connection state UI.
- [x] Add provider enable/disable controls.
- [x] Add model browser with search and filters.
- [x] Add default model and small model controls.
- [x] Store favorites/groups/tags in profile metadata.

Manual test:

```bash
cd packages/kilo-config-ui
bun run dev
open "http://127.0.0.1:3017?server=http://127.0.0.1:4097&directory=$PWD"
```

Expected result:

- Provider and model changes are visible in config files or profile metadata.
- The CLI model picker observes default model changes after reload.

### Checkpoint 7: Advanced Config UI

Status: Complete.

- [x] Add MCP server editor.
- [x] Add built-in and MCP tool inventory.
- [x] Add visual permission rule builder that preserves rule order.
- [x] Add TUI keybind editor with duplicate detection.
- [x] Add formatter and LSP configuration pages.

Manual test:

```bash
cd packages/kilo-config-ui
bun run dev
open "http://127.0.0.1:3017?server=http://127.0.0.1:4097&directory=$PWD"
```

Expected result:

- Edits produce sparse config patches.
- Existing JSONC comments and unrelated fields are preserved where practical.

### Checkpoint 8: Agent Builder

Status: Complete.

- [x] Add primary/subagent editor.
- [x] Save agents as canonical `agent/*.md` files.
- [x] Add prompt snippet insertion.
- [x] Compose model, provider, tools, MCP tools, and permissions visually.
- [x] Add generated markdown preview and validation.

Manual test:

```bash
# Terminal 1
cd packages/opencode
bun run --conditions=browser ./src/index.ts serve --hostname 127.0.0.1 --port 4097

# Terminal 2
cd packages/kilo-config-ui
bun run dev
open "http://127.0.0.1:3017?server=http://127.0.0.1:4097&directory=$PWD"
```

Expected result:

- A created agent appears in the CLI agent selector.
- A created subagent appears as a Task-tool selectable subagent.

### Checkpoint 9: Daemon Manager

Status: Complete.

- [x] Add daemon state file and lock handling.
- [x] Add authenticated daemon startup with random local token.
- [x] Add daemon health/version probing.
- [x] Add `kilo daemon status/start/stop/restart` commands.
- [x] Keep daemon usage opt-in while dashboard and APIs stabilize.

Manual test:

```bash
kilo daemon start
kilo daemon status
kilo daemon stop
```

Expected result:

- Daemon starts in the background, reports health/version/port, and stops cleanly.

### Checkpoint 10: Default Daemon And VS Code Replacement

Status: CLI default complete; VS Code replacement deferred.

- [x] Add TUI/run attach mode against the daemon.
- [x] Add fallback for daemon startup or attach failures.
- [x] Add `KILO_NO_DAEMON=1` escape hatch.
- [ ] Make VS Code open or embed the new dashboard.
- [ ] Deprecate duplicated settings UI after parity is reached.

Manual test:

```bash
kilo
kilo run "say hello"
```

Expected result:

- CLI commands attach to the daemon when enabled.
- Users can still bypass daemon mode when needed.

## Architecture

### Daemon Layer

Add a Kilo-owned daemon manager under something like:

`packages/opencode/src/kilocode/daemon/`

Responsibilities:

- Start daemon on first CLI invocation.
- Reuse existing daemon if healthy.
- Store daemon metadata under Kilo global state/config, for example:
- `pid`
- `port`
- `hostname`
- `auth token`
- `version`
- `startedAt`
- `log path`
- Use a lock file to avoid concurrent CLI calls spawning multiple daemons.
- Detect stale daemons by pid, health endpoint, and version mismatch.
- Restart on upgrade or corrupt state.
- Keep `kilo serve` as explicit foreground mode for servers/headless use.

New CLI commands:

- `kilo daemon status`
- `kilo daemon start`
- `kilo daemon stop`
- `kilo daemon restart`
- `kilo dashboard` or `kilo config ui`

Security requirements:

- Default daemon binds to `127.0.0.1`.
- Generate a random local token/password on first start.
- Never expose unauthenticated config/session/tool APIs.
- Prefer a one-time browser launch token that sets an `HttpOnly; SameSite=Strict` cookie, then redirects to a clean dashboard URL.
- Require explicit user config for external host binding.

### Server/API Layer

Use the existing server as the foundation, but add missing configuration APIs.

Existing useful APIs:

- `GET /config`
- `PATCH /config`
- `GET /global/config`
- `PATCH /global/config`
- provider list/auth endpoints
- config warning endpoints

Needed new APIs:

- `GET /global/health`
- `POST /global/shutdown` or daemon-local shutdown equivalent
- `GET /config/sources?directory=...`
- `GET /config/effective?directory=...&profile=...`
- `GET/PATCH /tui/config`
- `GET /profiles`
- `POST /profiles`
- `PATCH /profiles/:id`
- `DELETE /profiles/:id`
- `POST /profiles/:id/activate`
- `GET /tools`
- `GET /mcp/tools`
- `POST /providers/custom/models`
- `GET /config/schema` or equivalent metadata for UI form generation

Important: the dashboard should write sparse patches, not the fully resolved config. Resolved config contains inherited values and internal data that should not be written back wholesale.

### Profile System

Recommended model: profiles are named config overlays, not a giant new top-level object in `kilo.json`.

Proposed storage:

- Global profiles:
- `~/.config/kilo/profiles/<profile-id>/kilo.jsonc`
- `~/.config/kilo/profiles/<profile-id>/tui.jsonc`
- `~/.config/kilo/profiles/<profile-id>/agent/*.md`
- `~/.config/kilo/profiles/<profile-id>/command/*.md`
- Project profiles:
- `.kilo/profiles/<profile-id>/kilo.jsonc`
- `.kilo/profiles/<profile-id>/tui.jsonc`
- `.kilo/profiles/<profile-id>/agent/*.md`
- `.kilo/profiles/<profile-id>/command/*.md`
- Profile metadata:
- `~/.config/kilo/profiles/index.jsonc`
- `.kilo/profiles/index.jsonc`

Profile metadata can store UI-only fields:

- display name
- description
- color/icon
- tags
- model favorites
- model groups
- last active profile
- profile templates
- dashboard ordering

Proposed precedence:

- global base config
- active global profile
- project base config
- active project profile
- env/content/cloud/managed overlays keep their current special precedence

This gives users a global “Work” profile, a global “Personal” profile, and optional project-specific variants without replacing existing config semantics.

### New Dashboard Package

Create a new workspace package:

`packages/kilo-config-ui`

Use:

- SolidJS
- Vite
- `@kilocode/kilo-ui`
- `@kilocode/sdk`
- `ThemeProvider defaultTheme="kilo"`
- Storybook/visual tests later using existing Kilo UI patterns

Do not reuse the VS Code `KiloProvider.ts` bridge. Instead, build a direct SDK/HTTP data layer.

Recommended app layout:

- Dashboard overview
- Profiles
- Providers
- Models
- Agents
- Tools
- MCP
- TUI
- Formatters/LSP
- Permissions
- Prompt snippets/commands
- Import/export
- Diagnostics/warnings

### Settings UI Reuse Strategy

Reuse from VS Code settings where practical:

- provider catalog logic
- provider visibility logic
- custom provider validation
- custom provider model card/form ideas
- model selector concepts
- `SettingsRow`-style layout patterns

Do not directly reuse:

- VS Code message transport
- VS Code settings persistence
- VS Code-specific CSS variable assumptions
- `KiloProvider.ts`
- sidebar/editor webview shell

Long-term, the VS Code extension should either:

- open the daemon dashboard, or
- embed the same `packages/kilo-config-ui` screens with a VS Code adapter.

The dashboard should become the source of truth to avoid maintaining two settings products.

## Feature Plan

### Providers

Capabilities:

- list connected, available, disabled, env-sourced, and custom providers
- configure API keys without committing secrets to project config
- support OAuth providers
- create OpenAI-compatible custom providers
- fetch models from custom provider endpoints
- enable/disable providers
- show source/provenance: env, auth store, global config, project config, profile, managed config

Important rule: secrets should prefer auth storage or env vars, not project files.

### Models

Capabilities:

- browse all available models
- search/filter by provider, capability, context size, cost, tags
- set default model and small model
- set per-agent model
- mark favorites
- create model groups
- tag models
- hide deprecated/unwanted models
- preview final `provider/model` IDs

Favorites/groups/tags should probably live in profile metadata, not `Config.Info`, unless runtime behavior needs them.

### Tools

Capabilities:

- list built-in tools
- show descriptions
- show current permission status
- show whether tool is available to current agent/profile
- later: show MCP tools alongside built-in tools

### MCP

Capabilities:

- add local MCP server
- add remote MCP server
- configure env vars, headers, OAuth, timeout
- enable/disable servers
- inspect tools exposed by each MCP
- configure MCP tool permissions using existing permission keys like `server_tool`

### TUI Configuration

Capabilities:

- edit `tui.json[c]`
- theme selector
- keybind editor
- conflict detection for duplicate keybinds
- scroll speed/acceleration
- diff style
- mouse mode
- plugin enablement

Important: do not write TUI settings into `kilo.json`.

### Formatters/LSP

Capabilities:

- configure formatter commands
- map formatters to extensions
- enable/disable formatters
- configure LSP commands
- map LSP servers to extensions
- validate command arrays visually

### Permissions

Capabilities:

- visual permission rule builder
- preserve object/rule order
- support scalar and pattern forms
- support `allow`, `ask`, `deny`, `null`
- show final effective permission by scope
- warn when broad rules override specific rules
- support agent-level permissions

Important: permission ordering matters. The UI must preserve order and explain precedence.

### Agent Builder

This is the differentiating feature.

Capabilities:

- create primary agents and subagents
- configure:
- name
- description
- mode: primary/subagent/all
- model
- provider/model variant
- temperature/top-p/options
- max steps
- color
- permissions
- enabled tools
- MCP tools
- prompt
- prompt snippets
- allowed subagents
- save as canonical `agent/*.md` where possible
- support import/export as markdown agent files
- preview generated frontmatter/body
- validate before saving

Later advanced workflow feature:

- visual graph of agents/subagents
- define handoff rules
- define which subagent can call which subagent
- define shared prompt snippets
- define per-agent MCP/tool permissions
- package/share an agent workflow as a profile template

## Implementation Phases

### 1. Spec And Contracts

Deliverables:

- profile storage spec
- daemon state file spec
- dashboard route names
- config provenance response shape
- auth/security design
- migration behavior
- UI information architecture

No risky code yet.

### 2. Config API Foundation

Deliverables:

- config source/provenance API
- TUI config read/write API
- profile read/write/activation APIs
- validation API for pending config patches
- tests for global/project/profile precedence

Most logic should live under `packages/opencode/src/kilocode/`.

### 3. Daemon Foundation

Deliverables:

- daemon manager
- lock/state handling
- authenticated local daemon startup
- health/version checks
- status/start/stop commands
- optional attach mode for CLI clients

Rollout should be opt-in or experimental first, not forced as default immediately.

### 4. Dashboard Scaffold

Deliverables:

- `packages/kilo-config-ui`
- Solid/Vite app
- Kilo UI theme
- SDK client
- auth bootstrap
- dashboard shell
- config warning display
- global/project/profile scope selector

### 5. Providers And Models

Deliverables:

- provider cards
- auth/connect/disconnect
- custom provider flow
- model browser
- default/small model controls
- favorites/groups/tags metadata

### 6. Profiles

Deliverables:

- profile list/create/duplicate/delete
- global/project activation
- effective config preview
- diff against base/global/project
- import/export profile bundle

### 7. Advanced Config Pages

Deliverables:

- MCP page
- tools page
- permissions editor
- TUI keybind editor
- formatters/LSP page

### 8. Agent Builder

Deliverables:

- visual agent editor
- subagent mode support
- prompt editor/snippet insertion
- permission/tool/MCP composition
- markdown agent import/export
- validation and preview

### 9. VS Code Replacement Path

Deliverables:

- extension opens or embeds new dashboard
- old settings panel becomes compatibility/deprecated path
- shared config UI components replace duplicated VS Code settings logic
- VS Code-only settings either move into CLI config or remain in a small VS Code-specific section

### 10. Default Daemon Rollout

Deliverables:

- migrate TUI/run to attach to daemon
- keep fallback to in-process/worker mode
- detect daemon failures cleanly
- document escape hatch like `KILO_NO_DAEMON=1`
- make daemon default only after stability

## Merge-Minimizing Strategy

Because `packages/opencode` is shared with upstream OpenCode:

- Put daemon/profile/dashboard-specific logic in Kilo-owned paths like `src/kilocode/daemon`, `src/kilocode/profile`, `src/kilocode/config-ui`.
- Keep shared file changes minimal:
- CLI command registration
- server route hook
- config loader hook for profile overlays
- static UI route registration
- Mark unavoidable shared-file additions with narrow `kilocode_change` comments.
- Put tests under `packages/opencode/test/kilocode/`.
- Run `bun run script/check-opencode-annotations.ts` after implementation work touches shared opencode files.

## Main Risks

- Daemon security is the highest-risk area; config and session APIs cannot be exposed unauthenticated.
- Profile precedence can become confusing if not defined before coding.
- Writing resolved config back to files would corrupt user intent; only sparse patches should be written.
- TUI config is separate from main config and needs dedicated API/storage.
- VS Code settings currently mix CLI config and VS Code-local settings; not everything can move 1:1.
- Agent builder can become too broad; it should start by generating existing agent markdown files before inventing a new workflow runtime.
- Default daemon behavior is a breaking UX shift; it should be phased in behind explicit commands/flags first.

## Recommended First Iteration Scope

Start with the foundation, not the full UI:

- Define profile storage and precedence.
- Define daemon auth/state/health lifecycle.
- Add config provenance and TUI config APIs.
- Scaffold the dashboard with overview, config warnings, scope selector, and provider/model read-only views.
- Only then add write flows and agent builder.

Implementation is proceeding checkpoint by checkpoint. Continue only after the current checkpoint is manually validated or revised.
