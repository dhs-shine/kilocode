# Sandbox Network Destination Allowlist Plan

## Goal

Allow users to keep sandbox network restriction enabled while granting sandboxed agent tools access to a small set of exact network destinations. The primary example is allowing HTTPS access to GitHub so `gh` and HTTPS Git operations can work without granting unrestricted outbound access.

The implementation must preserve the existing deny-all behavior when no destinations are configured. A configured list must not be implemented as a best-effort URL check or proxy environment convention. Direct sockets, child processes, alternate proxy settings, and unsupported execution paths must not bypass the policy.

## Baseline

- Target the sandbox config promoted on `origin/main` by PR #12049, commit `323ec11576`, where settings live under the root `sandbox` object.
- `@kilocode/sandbox` already defines `network.mode: "proxy"` and `allowedHosts`, but deliberately rejects them as unsupported in `packages/kilo-sandbox/src/network.ts`.
- Linux deny mode uses a Bubblewrap network namespace. macOS deny mode uses a Seatbelt outbound-network denial.
- `sandbox.writable_paths` is global-only. Project config may enable sandboxing or deny network access, but may not widen authority.
- Issue #11675 describes the trusted-proxy direction. This plan changes its proposed project-default scope because repository-controlled config must not grant network authority.
- PR #11702 contains relevant Linux Unix-socket authority hardening, but it is stale, conflicted, and has an unresolved critical review finding. Rebase and resolve that work, or land equivalent hardening, before exposing proxy mode.

## Security Model

### Required Guarantees

- Empty or absent `allowed_hosts` means the current deny-all network policy.
- A non-empty list allows only exact configured host and port pairs through a trusted Kilo proxy.
- Model-originated execution inside the documented sandbox boundary has no direct route to the host network or host IPC authority in restricted modes.
- The proxy resolves DNS and opens the destination connection. The sandboxed process cannot supply a different resolved address.
- Every HTTP request and HTTPS `CONNECT` authority is checked independently, including requests made after redirects.
- Invalid config, unsupported platforms, proxy startup failure, proxy death, resolver failure, and backend setup failure all fail closed for network-restricted execution.
- Policy is immutable for one session and inherited monotonically by subagents, forks, and worktree moves.
- A repository, `KILO_CONFIG_CONTENT`, or another local config source cannot add destinations.
- Concurrent sessions cannot reuse each other's proxy endpoint or broader destination policy.
- Security errors and logs contain only canonical authorities and denial reasons, never credentials, URL paths, queries, headers, or request bodies.

### Explicit Boundaries

- An allowed destination is an egress authority, not a repository or API-action permission. Allowing GitHub may expose readable files and inherited GitHub credentials to any GitHub resource those credentials can access.
- This is not a data-loss-prevention system. Data can still be sent to an allowed service, included in model inference traffic, or handled by explicitly trusted integrations outside the sandbox boundary.
- Provider and model inference remain outside model-tool network policy.
- User-installed plugin hooks remain trusted host code and must be documented as outside this boundary. The proxy-only guarantee does not apply to plugin loading or hook code.
- Local and remote MCP clients remain trusted host integrations, but every model-facing MCP tool, prompt-resource, and delegated request path is unavailable in restricted sessions until MCP execution is routed through the same enforceable policy. A local transport is not proof that the MCP server stays offline.
- Version one supports HTTP and HTTPS, including HTTPS Git. SSH, `git://`, arbitrary TCP forwarding, UDP, QUIC, SOCKS, CIDR ranges, wildcard hosts, and allow-on-first-use prompts are out of scope.
- Windows remains unsupported. Configuring an allowlist on an unsupported backend must not silently produce unrestricted network access.
- HTTPS `CONNECT` grants a byte tunnel to the approved resolved address and port. It cannot restrict GitHub organization, repository, URL path, or API operation. Document this clearly rather than presenting domain access as content-level isolation.

## Configuration And UX

Use the existing network master switch and add a global-only list:

```jsonc
{
  "sandbox": {
    "enabled": true,
    "network": "deny",
    "allowed_hosts": [
      "github.com:443",
      "api.github.com:443"
    ]
  }
}
```

- Keep `sandbox.network` as `"allow" | "deny"` for compatibility.
- Treat `network: "deny"` plus an empty list as deny-all.
- Treat `network: "deny"` plus a non-empty list as the internal proxy mode.
- Keep the list stored but inactive when `network` is `"allow"`.
- Accept exact DNS names and canonical IP literals with an optional port. A missing port means `443`.
- Normalize DNS case, one trailing dot, IDNA ASCII form, IPv4, IPv6, and port representation before storing the effective policy.
- Reject schemes, user information, paths, query strings, fragments, whitespace/control characters, empty labels, ambiguous numeric IP forms, wildcards, and ports outside `1..65535`.
- Match exact hosts only. `github.com` must not match `api.github.com` or `evilgithub.com`.
- Version one accepts only globally routable destinations. Deny DNS results and IP literals in loopback, link-local, multicast, unspecified, private, reserved, metadata, IPv4-mapped, IPv4-embedded, DNS64/NAT64-encoded blocked ranges, or other non-global classes.
- Add **Allowed Network Destinations** below **Restrict Network Access** in the Sandboxing settings page, using the same add/remove interaction as writable paths.
- Save through `updateGlobalConfig`; do not offer project scope for an authority-widening list.
- Explain in the UI that GitHub CLI normally needs both `github.com:443` and `api.github.com:443`, exact requirements vary by workflow, and SSH remotes remain blocked.
- Show validation errors before save and repeat validation in the CLI config decoder. UI validation is not the security boundary.

## Implementation Plan

### 1. Protect Sandbox Policy Integrity

- Extend `packages/opencode/src/kilocode/sandbox/config.ts` with `allowed_hosts` parsing and canonicalization delegated to `@kilocode/sandbox`.
- Keep the shared `packages/opencode/src/config/config.ts` change limited to its existing `SandboxConfig` integration point.
- Resolve sandbox authority separately from normal deep config merging. Accumulate a trusted global baseline and then apply local restrictions so a local `network: "deny"` explicitly clears inherited destinations instead of accidentally retaining a global proxy list.
- Define widening sources explicitly rather than inferring trust from a path outside the worktree. Project config, `KILO_CONFIG_CONTENT`, `KILO_CONFIG`, `KILO_CONFIG_DIR`, and other environment-selected sources cannot add destinations unless a separate user-authorized trust mechanism is designed. Inventory managed organization and extension global overlays and classify each source deliberately.
- Keep `SandboxConfig.scope()` as the minimal integration point, but preserve enough source provenance for the final monotonic resolver to distinguish a global proxy policy from a local deny-all restriction.
- Store the canonical network policy in `SandboxStore.Snapshot`: mode plus sorted, deduplicated destinations.
- Store resolved writable-path exceptions in the same protected snapshot while touching this authority model. The current per-invocation config reload lets a sandboxed command edit global config and widen later tool calls.
- Preserve old snapshots safely: old `deny` snapshots migrate to deny-all, and old `allow` snapshots remain unrestricted. They never acquire destinations during migration.
- Make inheritance monotonic. Deny-all wins over proxy; proxy wins over unrestricted; two proxy policies intersect rather than union; inherited writable paths cannot become broader than the parent snapshot.
- Remove every authority-bearing global config root and environment-selected config directory from sandbox writable roots, or mount the whole root read-only. Protecting only currently existing config files is insufficient because a process could create another recognized filename, atomically replace one, or introduce a symlink. The settings UI and unsandboxed user actions can still update config.
- Change `SandboxPolicy.execute()` so an enabled `deny` or `proxy` snapshot can never call `unrestricted(effect)` when required support is unavailable. Return a structured sandbox-unavailable error, and remove restricted-mode backend paths that return the original launch command. Only an explicitly disabled snapshot may execute unrestricted.
- When a session transitions from unrestricted to sandboxed, terminate and await all session-owned background processes and PTYs, invalidate notebook execution, and revoke stale model-facing delegated handles before reporting the sandbox active. If safe revocation is impossible, refuse activation with a clear error.
- Update session documentation so changes to writable paths and allowed hosts apply to new sessions, not already initialized sessions.

### 2. Close Existing Model Execution Escapes

- Block `interactive_terminal` while sandboxing is active until its PTY process uses `prepareSandbox()` like the shell tool.
- Block `notebook_execute` while sandboxing is active because VS Code executes the selected kernel outside the CLI sandbox.
- Keep background-process start/restart blocked while sandboxing is active.
- Deny all local and remote MCP calls in `deny` and `proxy` modes unless a future MCP implementation can prove that the server and its descendants use the same network boundary. Cover `mcp.tools()`, tool invocation, prompt resource expansion, stale handles, and session-triggered reconnects, not only the common tool-call wrapper.
- Keep MCP process startup and unrelated background lifecycle explicitly in the trusted-integration boundary for version one. A restricted session must not cause model-directed traffic through those shared clients. If that distinction cannot be enforced with the shared `MCP.Service`, make MCP clients session/policy-scoped before shipping.
- Keep custom and opaque network-capable tools denied in restricted modes.
- Add mandatory sandbox capability metadata to the central registration path for every model-facing process, PTY, notebook, MCP, delegated host action, or direct network client. Fail an inventory test when a registered capability is unclassified.
- Expand `script/check-model-tool-network.ts` as defense in depth, but do not rely on source scanning alone to discover every execution path.
- Add invocation-time checks in addition to tool-list filtering so stale tool handles cannot bypass a changed or restored session policy.

### 3. Add One Canonical Destination Policy

- Add a Kilo-owned module such as `packages/kilo-sandbox/src/destination.ts` for parsing, normalization, matching, and safe display.
- Represent the runtime policy with canonical host and port values rather than repeatedly parsing user strings.
- Use one matcher for config validation, process proxy requests, and first-party in-process HTTP tools.
- Resolve DNS only after the authority matches the configured list.
- Validate every returned A and AAAA address. Reject the lookup if any selected connection could target a forbidden address class.
- Normalize IPv4-mapped and embedded IPv6 forms before classification, and reject DNS64/NAT64 results that encode a blocked IPv4 destination.
- Bind authorization to the address actually passed to `connect`; do not validate a hostname and then let another library resolve it again.
- Bound DNS result count, connection attempts, timeouts, header sizes, request-line size, and concurrent connections.

### 4. Implement A Scoped Trusted Proxy

- Add a Kilo-owned HTTP/HTTPS proxy in `packages/kilo-sandbox`, acquired and released through the existing Effect scope used by backend launch preparation.
- Create one immutable proxy policy per sandboxed execution, or an equivalently isolated per-session service whose policy can never expand.
- Require an unguessable per-execution credential on every proxy request. Store it only in process environment or inherited launch state, not in a readable policy file.
- Strip all inherited upper- and lower-case proxy variables before installing sandbox-owned `HTTP_PROXY` and `HTTPS_PROXY` values. Do not install `ALL_PROXY`.
- Accept HTTP absolute-form requests and HTTPS `CONNECT` only. Reject origin-form forwarding, malformed authorities, conflicting framing, unsupported methods for tunneling, and proxy chaining.
- Resolve and dial from the trusted proxy after policy authorization. Never let the client select an IP for an allowed DNS name.
- Revalidate each HTTP request on a reused connection and each new `CONNECT`. Redirects to unlisted destinations then fail on the next proxy request.
- Tear down listeners, relay processes, sockets, credentials, and active tunnels when the tool scope ends or is aborted.
- Surface deterministic permission errors. Never retry a failed proxy request through unrestricted `fetch` or the host network.

### 5. Enforce Proxy-Only Egress On Linux

- Keep Bubblewrap `--unshare-net` enabled for both deny-all and proxy modes.
- Start a small Kilo-owned relay inside the isolated namespace. Sandboxed clients connect only to its loopback listener; the relay forwards to the authenticated host proxy over one private transport.
- Do not make the host network namespace visible and do not rely on proxy environment variables for enforcement.
- Put the host-side transport under the protected sandbox-policy root, use a unique directory and socket per execution, and make it non-writable by the sandbox profile.
- Replace the host-wide read-only socket view with a default-deny IPC design for proxy mode. The namespace must receive a socket-free mount view and then expose only the dedicated relay transport. A denylist of known Docker, SSH/GPG agent, D-Bus, Wayland, and runtime sockets is useful defense in depth but is not sufficient.
- Rebase and incorporate the useful discovery, environment scrubbing, masking, and capability-reporting work from PR #11702 after resolving its current review finding.
- Prevent arbitrary pathname sockets, abstract sockets, and inherited connected file descriptors from carrying host authority into the sandbox. If Bubblewrap cannot provide this boundary without an additional helper, proxy mode remains unsupported on Linux until a default-deny mechanism exists.
- Ensure the proxy transport is the only exposed host socket. The trusted proxy must enforce the same policy even if a sandboxed process speaks to that socket directly instead of using the relay.
- Extend backend support probing to verify network namespace creation, relay startup, proxy transport, an allowed request, and a denied direct connection.
- If any required capability is unavailable, report proxy mode as unavailable and deny restricted execution rather than returning the original launch command.

### 6. Enforce Proxy-Only Egress On macOS

- Keep the general Seatbelt `network-outbound` denial in proxy mode.
- Start the trusted proxy on a random loopback port and generate a Seatbelt rule that permits outbound TCP only to that exact address and port.
- Deny general inbound networking in restricted modes unless a narrowly tested runtime requirement proves it is necessary.
- Remove or narrow resolver-related Mach and system-socket allowances in proxy mode so an untrusted process cannot use DNS queries as a separate egress channel. The trusted proxy performs DNS outside Seatbelt.
- Prove that arbitrary filesystem Unix sockets, Mach services, and inherited connected descriptors cannot carry proxy, credential, or network authority around the loopback rule. If Seatbelt cannot express that boundary, proxy mode remains unsupported on macOS.
- Add a real support probe for the exact Seatbelt rule. Do not infer proxy support from the presence of `/usr/bin/sandbox-exec`.
- If Seatbelt cannot express and enforce proxy-only loopback access without another route, do not expose allowed hosts on macOS. Keep deny-all available and report the allowlist capability as unsupported.

### 7. Route First-Party HTTP Tools Through The Same Policy

- Replace the current allow/deny-only `decorateHttpClient()` behavior with a policy-aware client that uses the scoped trusted proxy in proxy mode.
- Ensure web fetch, web search, image generation, and future registered first-party HTTP tools receive this client from the existing network HTTP layer.
- Disable automatic direct-network fallback and ensure redirect hops use the proxy.
- Preserve call-local context so provider traffic and other trusted control-plane requests in the same `kilo serve` process remain outside the model-tool policy.
- Keep remote MCP, custom tools, and opaque delegated tools denied rather than assuming their internal clients honor Kilo's proxy.

### 8. Expose Capability And Status Honestly

- Extend backend support reporting to distinguish filesystem confinement, deny-all network isolation, proxy transport, and Unix-socket coverage.
- Include proxy-mode unavailability in sandbox status, SSE events, OpenAPI, and generated SDK types.
- Keep existing `allow` and deny-all behavior unchanged when `allowed_hosts` is absent.
- Regenerate the SDK after changing server schemas.
- Add the matching root sandbox schema field to the hosted cloud config schema in a companion cloud PR, following the config-schema process.

### 9. Add Settings, Documentation, And Release Notes

- Extend VS Code config types, the Sandboxing tab, English strings, locale keys, unit tests, accessibility tests, and the settings Storybook state.
- Use global config APIs only and keep configured destinations visible but disabled when sandboxing or network restriction is off.
- Document exact matching, default port behavior, unsupported protocols, platform support, session snapshot behavior, credential exposure, provider/plugin boundaries, and the difference between a host grant and repository permission.
- Include a GitHub HTTPS example and state that SSH remotes must be changed to HTTPS.
- Add a patch changeset describing destination exceptions from the user's perspective.
- Run source-link extraction if documentation or UI introduces or changes URLs.

## Testing Plan

### Parser And Policy Tests

- Exact DNS names, case, one trailing dot, IDNA, IPv4, bracketed IPv6, default port, and explicit ports.
- Suffix confusion, wildcard input, schemes, credentials, paths, fragments, whitespace, control bytes, embedded NUL, invalid labels, invalid ports, numeric IPv4 variants, IPv4-mapped IPv6, and IPv6 zone identifiers.
- Global config accepted; project config, `KILO_CONFIG_CONTENT`, `KILO_CONFIG`, and `KILO_CONFIG_DIR` cannot add hosts; local deny clears a global proxy policy rather than retaining its list.
- Cover user-global config, extension global overlay, managed organization config, remote config, home config directories, and every environment-selected config source with explicit trust expectations.
- Legacy snapshot migration, immutable session policy, config self-edit attempts, inheritance intersection, forks, worktree moves, and subagents.
- Attempts to create an absent recognized config file, atomically replace one, rename over one, or use a symlink cannot change authority for the current or a later session.
- Forced backend, proxy, and relay unavailability causes both a shell tool and an in-process HTTP tool to fail before making a controlled request.

### Proxy Unit Tests

- Allowed HTTP and `CONNECT`, unlisted authority, direct IP substitution, missing/incorrect auth, proxy chaining, malformed `CONNECT`, oversized headers, conflicting `Content-Length`/`Transfer-Encoding`, timeout, abort, and cleanup.
- Send distinctive proxy credentials, URL credentials, paths, queries, headers, and body markers through success and failure paths. Assert none appear in proxy logs, structured errors, spans, SSE/status events, tool output, or support diagnostics.
- Allowed-to-allowed redirect, allowed-to-denied redirect, redirect to localhost/private IP, scheme downgrade, and redirect loops.
- DNS public result, forbidden result, mixed A/AAAA answers, rebinding/rotation, CNAME result, resolver failure, IPv4-mapped IPv6, IPv4-embedded IPv6, DNS64/NAT64 metadata encoding, and connection pinned to the validated address.
- Concurrent sessions with different policies and credentials cannot cross-use endpoints.
- Proxy or relay death fails the active operation without unrestricted retry.

### Linux Integration Tests

- Real `curl`, HTTPS Git, and a controlled `gh`-compatible request work only for listed destinations.
- Direct TCP, UDP, raw `fetch`, `NO_PROXY`, `--noproxy`, alternate proxy variables, Git proxy config, child/grandchild processes, and direct resolved-IP connections remain denied.
- An arbitrary host Unix socket under a nonstandard readable path, abstract sockets, inherited connected TCP/Unix descriptors, container/runtime sockets, and credential-agent sockets remain inaccessible in proxy mode.
- The in-namespace relay can reach only the authenticated trusted proxy and is removed after completion.
- Backend capability probe and failure paths are covered on a real Linux runner.

### macOS Integration Tests

- Real `curl` and controlled HTTPS requests reach the exact proxy listener and listed destination.
- Every other loopback port, external IPv4/IPv6 target, UDP target, direct DNS path, inbound listener, alternate proxy, arbitrary filesystem Unix socket, inherited connected descriptor, and child/grandchild route is denied.
- Seatbelt support probe failure and proxy termination fail closed.

### Tool Boundary Tests

- First-party HTTP tools use the allowlist and reject unlisted redirects.
- `interactive_terminal`, `notebook_execute`, background-process start/restart, custom tools, opaque network tools, MCP tools, MCP prompt resources, and stale delegated handles cannot bypass restricted modes.
- Enabling a sandboxed session terminates or rejects activation around an already-running session-owned background process, PTY, notebook execution, or delegated handle before that authority can be reused.
- A controlled plugin hook demonstrates and documents the trusted-plugin boundary rather than being accidentally presented as confined.
- Provider/model traffic remains functional and call-local while a concurrent model tool request is denied.
- Static architecture checks require explicit classification for every newly added model-facing execution or network path.

### UI And Documentation Tests

- Add/remove/save, duplicate normalization, invalid entry display, disabled states, keyboard operation, accessible labels, and global-only persistence.
- Update visual regression coverage for the Sandboxing settings panel.
- Validate the docs build and Markdown table formatting.

## Validation Commands

- `bun run typecheck && bun run test` from `packages/kilo-sandbox/`.
- Targeted sandbox tests and `bun run typecheck` from `packages/opencode/`.
- Linux sandbox integration tests from `packages/core/` on a Linux runner.
- `bun run typecheck`, `bun run lint`, `bun run test:unit`, `bun run knip`, and affected visual regression tests from `packages/kilo-vscode/`.
- Docs tests/build for `packages/kilo-docs/`.
- `./script/generate.ts` from the repository root after server schema changes.
- `bun run script/check-model-tool-network.ts` and `bun run script/check-opencode-annotations.ts` from the repository root.
- `bun run script/extract-source-links.ts` when source links change.

## Manual Verification

- Configure only `github.com:443` and `api.github.com:443`, start a new sandboxed session, and verify `gh api /rate_limit` and `git ls-remote https://github.com/Kilo-Org/kilocode.git` work.
- Verify an HTTPS request to an unlisted controlled domain fails, including through `curl --noproxy`, a direct resolved IP, a child process, and a redirect from an allowed test host.
- Verify an SSH Git remote remains blocked and the error explains that version one supports HTTPS only.
- Run two sessions with disjoint destination lists and verify neither can use the other's proxy authority.
- Kill the scoped proxy during a request and verify the tool fails closed.
- Use the VS Code self-test environment to verify settings persistence, validation, status text, and the same allowed/denied shell flow on a supported platform.

## Delivery Sequence

1. Rebase onto `origin/main` at or after PR #12049.
2. Land policy-integrity, execution-path, and Unix-socket hardening without exposing `allowed_hosts`.
3. Land canonical destination parsing and trusted proxy tests behind the existing unsupported proxy mode.
4. Land Linux proxy transport and complete its real-runner security matrix.
5. Land macOS proxy transport only after the narrow Seatbelt policy and DNS/inbound tests pass.
6. Integrate first-party HTTP tools and capability reporting.
7. Expose global config and VS Code settings, regenerate SDKs, update docs, and add the changeset.
8. Request an independent security review focused on parser ambiguity, proxy smuggling, DNS rebinding, direct-socket bypass, IPC authority, cross-session leakage, and fail-open paths.

## No-Ship Gates

- Do not expose `allowed_hosts` while `proxy` mode can fall back to unrestricted networking.
- Do not expose it on a platform unless direct IP sockets, arbitrary host IPC sockets, inherited connected descriptors, inbound networking, and direct DNS are denied and only the trusted proxy transport is reachable.
- Do not expose it while model-facing PTY, notebook, MCP tool/resource, custom, or delegated execution can bypass the restricted policy, or while pre-existing session-owned execution survives activation.
- Do not expose it until widening config sources are explicitly trusted, authority roots are read-only, local restrictions are provenance-aware, snapshots are immutable, and inheritance is monotonic.
- Do not expose it until automated redaction tests prove proxy credentials and request content cannot leak through logs, traces, errors, status events, or tool output.
- Do not describe it as repository-level GitHub access or complete exfiltration prevention.
- If either platform implementation cannot satisfy its gates, retain deny-all on that platform and report the allowlist capability as unavailable.
