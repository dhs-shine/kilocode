# User-facing OpenCode mentions in PR #10507

Scope: lines added by the upstream-merge PR (`gh pr diff 10507` filtered to `^+` for `opencode` / `anomalyco`). Most hits are file paths under `packages/opencode/` or internal symbol/package names (`@opencode-ai/core`, `@opencode/v2/Auth`, OAUTH_DUMMY_KEY = `"opencode-oauth-dummy-key"`, schema names) — none of those reach end users.

The notable user-facing items below are all introduced by the new "custom provider" flow upstream added to the TUI provider dialog.

## TUI strings rendered to users

File: `packages/opencode/src/cli/cmd/tui/component/dialog-provider.tsx`

1. Line 89 — prompt description (shown when user picks "Other" provider):
   > "This only stores a credential. Configure the provider in opencode.json to use it."

2. Line ~382 — toast after saving an unknown provider credential:
   > "Saved credential for ${providerID}. Configure it in opencode.json to use it."

   Kilo's primary config file is `kilo.json` / `kilo.jsonc` (with `opencode.json` accepted as a fallback per `config.ts:422`). The user-visible reference to `opencode.json` is upstream wording.

3. Lines 51-56 — hardcoded provider description map embedded in the shared upstream file:
   ```ts
   description: {
     opencode: "(Recommended)",
     anthropic: "(API key)",
     openai: "(ChatGPT Plus/Pro or API key)",
     "opencode-go": "Low cost subscription for everyone",
   }[provider.id],
   ```
   Kilo already overrides `PROVIDER_PRIORITY` (and has its own `PROVIDER_DESCRIPTIONS` in `src/kilocode/cli/cmd/tui/component/dialog-provider.tsx`), but the upstream description map remains the one actually consulted by `providerOptions()`. The `opencode` / `opencode-go` entries would only render if a provider with those IDs is present in `sync.data.provider_next.all`, which is not the case for stock Kilo today — but the strings are now baked into shared TUI code.

## Non-user-facing OpenCode references (informational, not problems)

- Internal package / module / schema names: `@opencode-ai/core/*`, `@opencode-ai/ui#test` (turbo task), `@opencode/v2/Auth`, `@opencode/v2/Model`, `OAUTH_DUMMY_KEY = "opencode-oauth-dummy-key"`, schema literal `opencode: schema.make("opencode")`.
- Remote-config URL convention: `${url}/.well-known/opencode` in `src/config/config.ts:5058+` — external endpoint contract, the existing `kilocode_change` marker is preserved.
- Dev-only files: `notes/thoughts/...` plugin-design notes, `specs/effect/errors.md`, test fixtures (`test@opencode.test`, `app.opencode.ai` CORS test, `opencode#24432` regression comment, `.opencode/tool/emoji.ts` mock path).
- Build/publish comment: `// kilocode_change start - Kilo does not ship the opencode desktop app` in `script/publish.ts`.
- `.opencode-version` file used by the merge tooling.

## No matches found for

- Links to `opencode.ai` / `github.com/opencode-ai` / `github.com/anomalyco` in any user-facing surface (the only `opencode.ai` reference is inside a CORS unit test).
- Changes to `package.json` `name` / `description` / `displayName` / READMEs that surface "opencode" to users.

## Summary

Two genuine user-facing OpenCode mentions were added by the merge, both in the new TUI custom-provider flow (`dialog-provider.tsx` lines 89 and ~382), instructing users to "Configure the provider in opencode.json". A third location (hardcoded provider description map referencing `opencode` / `opencode-go`) is dormant under Kilo's current provider list but is now embedded in shared code. Everything else flagged is internal (package names, file paths, test fixtures, dev notes, build comments).
