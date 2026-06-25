# Native VS Code notebook tools

## Goal

Integrate live notebook inspection, editing, and execution into the Kilo VS Code extension without installing, configuring, or embedding a second MCP server. Expose the capabilities only to CLI backends started by the VS Code extension, while keeping Microsoft Jupyter optional.

## Decisions

- Expose three focused tools: `notebook_read`, `notebook_edit`, and `notebook_execute`.
- Use the existing authenticated CLI server, global SSE stream, and HTTP SDK for a correlated extension-host request/reply bridge.
- Require an explicit workspace-relative notebook path for every targeted operation.
- Resolve paths against the SSE request directory, enforce containment and ignore rules, and never fall back to the globally active notebook.
- Permit notebooks to be opened in the background for reads and edits.
- Never reveal/focus a notebook or invoke the kernel picker. Execution fails with an actionable error if no controller/kernel is selected.
- Keep Jupyter optional and use stable generic VS Code Notebook APIs and commands.
- Use separate `notebook_read`, `notebook_edit`, and `notebook_execute` permissions.
- Leave edits dirty and preserve VS Code undo/redo and existing autosave behavior.
- Require the expected notebook version for index-based mutations/execution and reject stale requests.
- `notebook_read` returns structure and source by default; `include_outputs: true` adds bounded text/error outputs.
- `notebook_edit` supports inserting, replacing, and deleting one cell per call.
- `notebook_execute` runs one explicit code cell per call and returns only the newly completed execution result.
- Enable the tools by default for VS Code sessions; omit them from other clients.
- Defer cell movement, bulk operations, search, outline generation, metadata editing, output clearing, rich MIME/images, variable introspection, and kernel-context inspection.

## Implementation plan

1. **Define a Kilo-owned notebook host protocol in the CLI.**
   - Add request, result, and structured error schemas under `packages/opencode/src/kilocode/`.
   - Include request ID, session ID, operation, relative notebook path, expected notebook version where applicable, cell index, and operation-specific arguments.
   - Define a typed bus event for pending notebook requests and ensure it is imported before the global bus/OpenAPI event union is constructed.

2. **Implement the pending request lifecycle.**
   - Follow the `Question.Service` pattern: store a deferred result, publish the request event, and await an authenticated reply.
   - Add cancellation, bounded timeout, instance-disposal cleanup, duplicate/late-reply handling, and typed rejection.
   - Add a pending-list operation so the extension can recover requests after an SSE/backend reconnect.
   - Ensure every terminal path removes pending state and listeners.

3. **Add authenticated Kilo HTTP endpoints.**
   - Extend the existing Kilo-owned API group rather than adding another shared upstream route integration where possible.
   - Add endpoints to list pending requests and reply/reject by request ID.
   - Preserve directory scoping through the existing SDK request conventions.
   - Regenerate `packages/sdk/js/` with `./script/generate.ts`; do not edit generated files manually.

4. **Register the three CLI tools.**
   - Add Kilo-owned tool implementations and register them through `packages/opencode/src/kilocode/tool/registry.ts` only when `Flag.KILO_CLIENT === "vscode"`.
   - Give each tool a narrow non-polymorphic schema and its dedicated permission.
   - `notebook_read`: list eligible/open notebooks when appropriate, or read one explicit notebook; include outputs only when requested.
   - `notebook_edit`: insert, replace, or delete one cell and require the expected notebook version.
   - `notebook_execute`: execute one explicit code-cell index and require the expected notebook version.
   - Return compact structured text suitable for the model and apply aggregate output limits.

5. **Implement a singleton extension-host notebook bridge.**
   - Add a service under `packages/kilo-vscode/src/services/notebook/` and instantiate it once from extension activation, not once per `KiloProvider`.
   - Subscribe through `KiloConnectionService.onEventFiltered()`, retain the SSE directory, deduplicate request IDs, and recover pending requests after reconnect.
   - Post results/rejections through the generated authenticated SDK client.
   - Dispose subscriptions and reject/stop in-flight work on extension shutdown or backend replacement.

6. **Implement a narrow VS Code notebook adapter.**
   - Resolve the explicit relative path against the request directory and verify containment and ignore/access policy before opening anything.
   - Prefer an already-open `NotebookDocument` so dirty in-memory state is authoritative; otherwise use `workspace.openNotebookDocument()` without showing it.
   - Never use active-editor fallback for targeted operations.
   - Normalize notebook/cell data into protocol-owned values rather than serializing VS Code objects.
   - Bound source/output sizes and represent omitted rich MIME/image data by MIME type and omission metadata.

7. **Implement reading and editing.**
   - Read cell index, kind, language, source, notebook version, and execution summary where stable.
   - Include only bounded text and error outputs when `include_outputs` is true; omit image/HTML/binary payload bodies in v1.
   - Build `WorkspaceEdit`/`NotebookEdit` operations for insert, replacement, and deletion so changes use the notebook model and undo stack.
   - Validate notebook version and cell index immediately before applying an edit.
   - Do not save automatically.

8. **Implement deterministic execution.**
   - Validate that the indexed cell is a code cell and that the notebook/version still matches.
   - Subscribe to notebook execution/output changes before dispatching the standard VS Code notebook execution command.
   - Correlate completion to the requested notebook and cell, requiring a new execution cycle rather than accepting stale `executionSummary` or existing outputs.
   - Return the resulting bounded text/error outputs and execution status.
   - Honor tool cancellation and timeout, clean up listeners in every path, and return actionable errors for no controller/kernel, closed document, changed cell, extension disconnect, or execution failure.
   - Do not reveal the notebook or invoke kernel selection.

9. **Add CLI tests.**
   - Verify VS Code-only tool registration and absence in other clients.
   - Verify each dedicated permission request.
   - Test event publication with session and directory context, reply completion, rejection, cancellation, timeout, disposal, pending recovery, and duplicate/late replies.
   - Test schemas, output bounds, stale-version failures, and structured errors using actual service behavior rather than duplicating implementation logic.

10. **Add extension tests.**
    - Unit-test path containment, worktree routing, ignore enforcement, open-document preference, background opening, version conflicts, cell validation, edit construction, output normalization/truncation, deduplication, reconnect recovery, cancellation, and listener cleanup.
    - Add an extension-host integration test with a test notebook serializer/controller. Open a temporary notebook, read live unsaved source, edit a cell, execute one cell, publish outputs, and verify the CLI receives the normalized result.
    - Include a no-controller test proving execution fails without changing focus.
    - Keep Jupyter-specific behavior as an optional manual/smoke validation, not a CI dependency.

11. **Release and verification.**
    - Add a user-facing minor changeset describing native notebook reading, editing, and execution in VS Code.
    - If substantial upstream MIT code is copied, retain its copyright and license notice; prefer adapting concepts and small helpers instead of porting the upstream MCP server or monolithic handlers.
    - Run targeted CLI tests and `bun run typecheck` from `packages/opencode/`.
    - Run extension typecheck, lint, targeted unit/integration tests, compile/package checks as needed, and `bun run knip` from `packages/kilo-vscode/`.
    - Run `bun run script/check-opencode-annotations.ts` and `bun run script/check-opencode-promise-facades.ts` when affected.
    - Confirm ordinary Kilo activation and static `.ipynb` reads still work with Jupyter absent.

## Failure and security requirements

- Do not start a localhost MCP server, allocate an extra port, or write user MCP configuration.
- Do not allow a request from one workspace/worktree to operate on a notebook outside its directory.
- Do not use editor focus as authority.
- Treat notebook outputs as sensitive and potentially unbounded.
- Treat kernel execution as arbitrary code execution with its own permission.
- Fail closed on stale notebook versions, missing kernels/controllers, disconnects, unsupported notebook providers, and malformed replies.
- Avoid fixed sleeps; use VS Code events and correlated request state.

## Out of scope

- Support for notebook operations in the standalone CLI/TUI or non-VS Code clients
- Automatic installation or activation of Microsoft Jupyter
- Automatic kernel selection or UI focus changes
- Cell movement or bulk editing
- Search and outline tools
- Rich image, HTML, widget, or arbitrary MIME transport
- Kernel namespace/variable introspection
- Editing notebook metadata or clearing outputs
