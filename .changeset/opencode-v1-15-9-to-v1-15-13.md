---
"@kilocode/cli": patch
"kilo-code": patch
---

Changes from opencode v1.15.9 to v1.15.13 upstream:

- Core Improvements: Added `headerTimeout` config for provider requests, with a 10s default for default OpenAI setups.
- Core Improvements: Experimental background agents now push updates without polling.
- Core Improvements: You can now set only `modalities.input` or `modalities.output` in config. (@robposch)
- Core Improvements: Remote-backed projects now resolve a stable project identity.
- Core Improvements: ACP integrations can now send prompts, slash commands, and usage updates through `acp-next`
- Core Improvements: Added WebSocket transport for OpenAI responses on supported channels (set KILO_EXPERIMENTAL_WEBSOCKETS=true)
- Core Improvements: Sessions can now store custom metadata through the API and SDK. (@shantur)
- Core Improvements: Config now loads from the opened location upward, so directory-specific settings and provider policies apply more predictably.
- Core Bugfixes: Dynamically added MCP servers now disconnect cleanly when removed.
- Core Bugfixes: DigitalOcean inference now uses your OAuth token directly instead of creating a MAK. (@Spherrrical)
- Core Bugfixes: Config loading now falls back cleanly when user info is unavailable.
- Core Bugfixes: Fixed Google tool calling after the upstream tool ID regression.
- Core Bugfixes: Experimental flags can now override the umbrella experimental flag.
- Core Bugfixes: Resumed sessions no longer continue orphaned interrupted tools. (@edevil)
- Core Bugfixes: OpenAI reasoning summaries now render as separate blocks.
- Core Bugfixes: Updated Google Vertex support for reasoning signatures.
- Core Bugfixes: The shell tool now advertises your configured timeout to the model.
- Core Bugfixes: Enabled adaptive reasoning controls for Anthropic Opus 4.7+ models
- Core Bugfixes: Allowed colons in passwords (@neriousy)
- Core Bugfixes: Sped up warm `acp-next` model and config switches
- Core Bugfixes: Improved first-session `acp-next` startup time
- Core Bugfixes: Kept OpenAI WebSocket response timeouts active
- Core Bugfixes: Retried failed OpenAI WebSocket streams before falling back
- Core Bugfixes: Handled `acp-next` permission prompts correctly
- Core Bugfixes: Used the persisted session directory for existing-session requests
- Core Bugfixes: Forwarded remote workspace request bodies correctly
- Core Bugfixes: Supported custom base URLs for OpenAI WebSocket responses (@Tarquinen)
- Core Bugfixes: Gateway Anthropic Opus 4.7+ adaptive reasoning now keeps summarized thinking instead of returning empty thinking blocks.
- TUI Improvements: Made the prompt resize with terminal width and added prompt size config. (@bjschafer)
- TUI Improvements: Added a workspace management dialog
- TUI Bugfixes: Accelerated diff viewer scrolling.
- TUI Bugfixes: External editors now open from the worktree directory when available.
- TUI Bugfixes: Kept session navigation working while prompt modes are open
- TUI Bugfixes: Restored the thinking spinner
- TUI Bugfixes: Surfaced subagent retry status
- TUI Bugfixes: Fixed opening editors from non-Git project paths (@OpeOginni)
- TUI Bugfixes: Wrapped inline tool rows now stay aligned, and failed inline tools can expand their error details in place.
- Extensions Improvements: Added a `dispose` hook for plugins.
- Extensions Bugfixes: Fixed Codex plugin requests to send the expected session ID header.
