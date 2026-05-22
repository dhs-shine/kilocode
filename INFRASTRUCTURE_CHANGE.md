# Infrastructure Review — PR #10507 (OpenCode v1.14.41 merge)

No `.github/workflows/`, `.github/actions/`, Docker, release-deploy, issue templates, or changelog automation files are touched. CI/release infrastructure is untouched. Generated SDK regen (`packages/sdk/js/src/v2/gen/*`, `packages/sdk/openapi.json`) follows from upstream API changes — expected, not a concern.

## Findings

### 🚩 Accidentally committed Gradle cache (must fix)
The following Gradle local-cache files were added under `packages/kilo-jetbrains/build-tasks/.gradle/` and should never be in version control:

- `packages/kilo-jetbrains/build-tasks/.gradle/9.4.1/executionHistory/executionHistory.bin`
- `packages/kilo-jetbrains/build-tasks/.gradle/9.4.1/executionHistory/executionHistory.lock`
- `packages/kilo-jetbrains/build-tasks/.gradle/buildOutputCleanup/buildOutputCleanup.lock`
- `packages/kilo-jetbrains/build-tasks/.gradle/buildOutputCleanup/cache.properties`
- `packages/kilo-jetbrains/build-tasks/.gradle/buildOutputCleanup/outputFiles.bin`
- `packages/kilo-jetbrains/build-tasks/.gradle/file-system.probe`

These are local Gradle build state, not infrastructure we want to ship. They are not from upstream opencode (this is a Kilo-only path). Remove from the PR and ensure `packages/kilo-jetbrains/build-tasks/.gradle/` is in `.gitignore`.

### `script/publish.ts` — Kilo behavior preserved ✅
Upstream added `desktop/scripts/finalize-latest-{json,yml}.ts` calls. They were imported as **commented-out** lines wrapped in `// kilocode_change start - Kilo does not ship the opencode desktop app` markers. Correct handling — Kilo's publish flow is preserved and the upstream additions are tracked for future merges.

### `turbo.json` — additive, OK
Adds `@opencode-ai/ui#test` and `@opencode-ai/ui#test:ci` task definitions. Pure additions to support upstream's new `packages/ui` test scripts. Does not override any Kilo-specific Turbo task. No conflict with our infra.

### `packages/ui/package.json` — additive scripts
Upstream adds `"test"` and `"test:ci"` scripts to `@opencode-ai/ui`. Matches the `turbo.json` additions. No Kilo override displaced.

### `packages/kilo-jetbrains/package.json`
Adds `"version": "7.3.5"` and empty `dependencies`/`devDependencies`/`peerDependencies` fields. Kilo-owned package; minor metadata addition, not infra-impacting.

### `packages/opencode/package.json`
Bumps `@agentclientprotocol/sdk` 0.16.1 → 0.21.0. Standard upstream dep bump, not a CI/release concern.

### Root `package.json`
Bumps `@types/node` 22.13.9 → 24.12.2. Worth noting because Node 24 typings can surface new lib type errors in shared code, but it does not change workspace/build infrastructure.

### `.opencode-version` and `bun.lock`
Standard merge artifacts — version bump to `v1.14.41` and corresponding lockfile updates. Expected.

## Summary

The only real concern is the accidentally committed Gradle cache directory under `packages/kilo-jetbrains/build-tasks/.gradle/` — those files must be removed before merging and the path added to `.gitignore`. All other infrastructure-touching changes are either upstream-additive (no override of Kilo-specific automation) or properly gated behind `kilocode_change` markers (`script/publish.ts`). No upstream CI/CD, release flow, or repo automation is being adopted in a way that displaces ours.
