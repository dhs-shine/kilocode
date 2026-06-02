---
title: "Development Patterns"
description: "Contributor patterns for Kilo architecture implementation"
---

# Development Patterns

This page collects implementation patterns contributors should follow when changing architecture-facing code.

{% callout type="info" title="Scope" %}
These are preferred patterns for new architecture-facing work. Existing modules may use older shapes; follow neighboring style unless you are intentionally refactoring that boundary.
{% /callout %}

## Source Map

| Concern | Source |
|---|---|
| Tool definition API | `packages/opencode/src/tool/tool.ts` |
| Tool implementation example | `packages/opencode/src/tool/read.ts` |
| Server API definitions | `packages/opencode/src/server/routes/` |
| SDK generation | `packages/sdk/js/` and `script/generate.ts` |
| Kilo-specific CLI code | `packages/opencode/src/kilocode/` |

## Module Export Pattern

For new public APIs, prefer flat ESM exports inside each module, then namespace re-exports from index files when callers need grouped access. Top-level exports are easier to tree-shake and work better with Node's type-stripping runtime.

```typescript
// packages/opencode/src/session/session.ts
export const create = fn(CreateSchema, async (input) => {
  // ...
})

export const list = fn(ListSchema, async (input) => {
  // ...
})

// packages/opencode/src/session/index.ts
export * as Session from "./session"
```

Prefer importing the specific export when possible. Use the namespace re-export (`Session.create`, `Session.list`) when a caller benefits from grouped module access or when preserving existing public shape.

Existing Kilo-owned namespace modules remain in the repo. Follow neighboring style unless you are intentionally migrating that boundary to flat ESM exports.

## CLI Server API

The CLI server uses Effect `HttpApi` and publishes OpenAPI-compatible HTTP + SSE surfaces consumed by `@kilocode/sdk`.

| Rule | Reason |
|---|---|
| Keep generated SDK output stable | Client packages and cloud services rely on the generated contract |
| Regenerate `packages/sdk/js/` after endpoint changes | SDK output is checked in and must match server API changes |
| Keep route spans and attributes stable | Telemetry, debugging, and cloud integrations depend on predictable metadata |
| Prefer Kilo-owned boundaries for Kilo-specific API additions | Shared OpenCode code is harder to merge across upstream updates |

## Tool Implementation

Tools use `Tool.define("id", Effect.gen(...))` with Effect Schema validation and typed execution.

```typescript
export const ExampleTool = Tool.define(
  "example",
  Effect.gen(function* () {
    return {
      description: "Example tool",
      parameters: Schema.Struct({
        value: Schema.String,
      }),
      execute(args, ctx) {
        return Effect.succeed({
          title: args.value,
          metadata: {},
          output: args.value,
        })
      },
    }
  }),
)
```

Use existing tool helpers, permission gates, and telemetry conventions before adding new abstractions. Tests should exercise actual implementation behavior rather than duplicating logic in mocks.

## Build System

| Area | Tooling |
|---|---|
| Package manager | Bun workspaces in the `kilocode` repo |
| Task orchestration | Turborepo |
| CLI and extension bundling | esbuild |
| Type checking | `tsgo` through `bun turbo typecheck` |
| Tests | Package-level Bun test or Vitest depending on package |
| Docs | Next.js, Markdoc, Mermaid, and custom Markdoc components |

## Documentation Changes

When adding or moving docs pages:

- Create the page under `pages/`.
- Update the matching navigation file in `lib/nav/`.
- Add redirects when removing or moving existing routes.
- Use compact markdown tables with unpadded cells.
- Use `/docs` prefix for docs image paths.

## Kilo-Specific Code Placement

Kilo CLI is a fork of upstream OpenCode. Prefer Kilo-owned directories and boundaries when possible:

| Prefer | Avoid unless necessary |
|---|---|
| `packages/opencode/src/kilocode/` | Broad edits to shared upstream files |
| `packages/opencode/test/kilocode/` | Shared tests that encode Kilo-only behavior without markers |
| Kilo packages such as `packages/kilo-vscode/` and `packages/kilo-docs/` | Refactors that increase upstream merge conflicts |

When shared upstream files must change, use `kilocode_change` markers according to repository policy.
