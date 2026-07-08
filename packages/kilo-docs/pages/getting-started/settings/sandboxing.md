---
title: "Sandboxing"
description: "Understand and configure filesystem write and network restrictions for agent tools"
---

# Sandboxing

The sandbox adds an operating-system boundary around agent tools. It limits where tools can write and, by default, blocks outbound network access from model-originated commands. This boundary applies even when a tool passes Kilo's permission checks.

The sandbox is **disabled by default**. It does not restrict filesystem reads. An agent can still read any file that your user account can read, but it can write only to explicitly allowed locations.

{% callout type="warning" %}
Sandboxing is experimental and is not available on Windows. If the macOS or Linux sandbox backend is unavailable, Kilo reports the reason and runs tools without sandbox confinement. The sandbox does not fail closed.
{% /callout %}

## Enable the sandbox

In the VS Code extension:

1. Open Kilo Code Settings using the gear icon ({% codicon name="gear" /%}).
2. Select **Sandboxing**.
3. Turn on **Sandbox**.
4. Keep **Restrict Network Access** on unless the agent's commands need outbound network access.
5. Save the settings.

The **Sandboxing** tab is visible to all macOS and Linux users, including when the sandbox is off. Windows users do not see the tab because no Windows backend is available.

You can also configure the default in the global `kilo.jsonc` file:

```json
{
  "experimental": {
    "sandbox": true,
    "sandbox_restrict_network": true,
    "sandbox_writable_paths": ["~/shared-output"]
  }
}
```

| Key | Default | Effect |
|---|---|---|
| `experimental.sandbox` | `false` | Use sandbox confinement by default for new sessions. |
| `experimental.sandbox_restrict_network` | `true` | Block outbound network access while filesystem confinement is active. Set this to `false` to allow network access without removing filesystem write restrictions. |
| `experimental.sandbox_writable_paths` | `[]` | Add writable files or directories outside the built-in writable locations. For security, only the global config can set these paths. |

## Filesystem restrictions

When the sandbox is active, agent tools can read files normally. The sandbox restricts writes, including creating, changing, renaming, and deleting files.

Writes are allowed in:

- The active project or worktree
- Kilo's data, cache, config, state, temporary, binary, log, and repository directories
- Paths listed in `experimental.sandbox_writable_paths`

Writes are denied everywhere else. The following rules still apply inside writable locations:

- `.git` directories are always read-only to sandboxed tools.
- Kilo's stored sandbox policy and preference files are read-only.
- A permission approval for a path outside the sandbox does not make that path writable. Add the path to **Additional Writable Paths** if the tool must modify it.
- Linked worktree sessions can write to their active worktree, not the primary checkout or sibling worktrees.

Shell commands and their child processes inherit the same restrictions. Kilo's file tools perform mutations through a sandboxed worker. Writable file handles are unavailable, so a tool that requires an open read-write handle may fail even for an allowed path.

{% callout type="info" %}
The sandbox is a write boundary, not a privacy boundary. It does not prevent an agent from reading files outside your project if your operating-system account can read them.
{% /callout %}

## Network restrictions

**Restrict Network Access** controls outbound network access independently of filesystem writes. Turning it off leaves the filesystem write restrictions active.

When network restriction is on, Kilo blocks:

- Outbound network access from model-originated shell commands and their child processes
- Requests made through Kilo's policy-aware first-party HTTP clients
- Remote MCP tool calls and custom or plugin tools that Kilo cannot prove will remain offline
- Built-in tools such as codebase search, semantic search, and LSP that may use opaque or indirect network access

Network restriction does not block:

- Provider and model inference traffic, so conversations with the selected model continue to work
- Local MCP server processes
- Plugin hooks that run outside the sandboxed tool execution
- Filesystem reads

This is not a system-wide firewall. It applies to the sandboxed tool execution boundary, not every Kilo, extension, or local process. Proxy environment variables are removed from sandboxed commands while network access is restricted.

## Session behavior

The config setting supplies the initial default for new sessions that do not have a saved preference. Use the lock button in the VS Code prompt or `/sandbox` in the CLI to change the current session. Your latest choice is saved as the default for future sessions in that project, takes precedence over the config default, and persists across restarts.

Each initialized session keeps its sandbox enabled state and network mode. Changing those settings affects new sessions; use the prompt control or `/sandbox` to change an existing session's enabled state. Changes to **Additional Writable Paths** are read when tools run and therefore also apply to existing sandboxed sessions.

Forked sessions retain the source session's confinement. Subagents inherit the stricter combination of the parent and child settings: sandboxing remains enabled if either requires it, and network remains blocked if either requires blocking.

Cloud sessions do not expose the local sandbox control because their tools do not run in your local sandbox.

## Platform support

| Platform | Backend | Notes |
|---|---|---|
| macOS | `sandbox-exec` (Seatbelt) | Uses `/usr/bin/sandbox-exec`. File reads and inbound networking remain allowed. |
| Linux | Bubblewrap (`bwrap`) | Uses system `/usr/bin/bwrap` or a bundled, SHA-256-verified binary. `KILO_BWRAP_PATH` can select another binary. Kilo probes filesystem and network namespace support before enabling confinement. |
| Windows | None | Unsupported. The VS Code settings and prompt controls are hidden, and enabling the config has no effect. |

## Limitations

- The sandbox supplements Kilo's permission system; it does not replace permission prompts or rules.
- Local MCP servers and plugin hooks execute outside the operating-system sandbox.
- Direct filesystem access inside trusted in-process integrations is covered only when the integration uses Kilo's sandbox-aware filesystem service.
- Starting or restarting a background process with the background-process tool is unavailable while sandboxing is active.
- On Linux, an additional writable path must already exist before Bubblewrap starts.
