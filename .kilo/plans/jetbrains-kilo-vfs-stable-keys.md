# JetBrains Kilo VFS Stable Keys Plan

## Goal
Make Kilo VFS editor files use stable, semantic identity so every distinct attachment can appear as a distinct Recent Files item, reopening the same logical VFS file reuses the same editor tab, and future VFS content kinds can be added without process-launch or random identifiers in their file keys.

## Findings
- The JetBrains plugin VFS is in `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/vfs/`.
- `KiloVfsManager` currently creates `launchId = System.currentTimeMillis().toString()` and passes it into `KiloPath` for every VFS file.
- `KiloPath` serializes `launchId`, `projectHash`, `kind`, and `params`; `KiloVirtualFile.equals/hashCode` includes the full `KiloPath`, so launch-scoped data is part of file identity.
- `KiloVirtualFile` is included in editor history but `isPersistedInEditorHistory()` returns `false`; keep persistence disabled for now and do not use volatile path keys as a persistence workaround.
- The attachment-specific discriminator `attachmentKey` already exists in this worktree and distinguishes duplicate file parts with the same `partId`; keep that deterministic discriminator, but remove the generic launch-scoped part of the VFS key.

## Target Design
- Treat a Kilo VFS file path as `projectHash + kind + stable params` only.
- Remove `launchId`, timestamps, random UUIDs, editor instance IDs, and process-local values from `KiloPath` and from all VFS path-building helpers.
- Canonicalize params before serializing a VFS path so two maps with the same key/value set produce the same path string regardless of insertion order.
- Keep each editor kind responsible for building stable params through a small kind-owned helper, rather than constructing ad hoc maps at call sites.
- For attachments, use stable params such as `sessionId`, `messageId`, `partId`, `attachmentKey`, and `directory`; keep `filename` and `mime` only as stable presentation/fetch hints.
- For future kinds, define one helper per kind:
  - Session UI: `sessionId` and `directory`.
  - Marketplace page: stable route or listing id, plus any stable item id needed to identify the page.
  - Generated/session artifacts: session id plus message/part/content id, with a deterministic content discriminator only when the backend lacks a unique part id.

## Implementation Steps
1. Update `KiloPath` to remove `launchId`; fields become `projectHash`, `kind`, and `params`.
2. Update `KiloVfsManager` to stop storing `launchId` and create paths from `project.locationHash`, `kind`, and canonicalized params.
3. Add a small canonicalization helper near the VFS core, for example sorting params by key before `KiloVirtualFileSystem.getPath(...)` serializes them and before decoded paths become `KiloVirtualFile` instances.
4. Update tests and test helpers that construct `KiloPath("launch", ...)` to the new stable constructor/signature.
5. Strengthen `KiloVfsManagerTest` or `KiloVirtualFileSystemTest` with assertions that:
   - serialized Kilo paths do not contain `launchId` or any launch value,
   - the same kind and same params produce the same serialized path across independent `KiloPath` constructions,
   - param insertion order does not change serialized path identity,
   - opening the same stable key twice reuses one editor tab,
   - opening two distinct stable keys creates two open files and two Recent Files entries.
6. Keep the existing attachment tests that assert duplicate `partId` attachments get distinct `attachmentKey` values and distinct history entries.
7. Update `AttachmentEditorKindTest` direct `KiloPath` construction to the new stable path shape and add an explicit assertion that attachment params do not include launch/time/random keys.
8. Keep `KiloVirtualFile.isPersistedInEditorHistory()` returning `false`; do not add persistence behavior in this fix.
9. Update the existing JetBrains changeset description if needed so release notes describe stable/distinct Kilo VFS attachment tabs from the user perspective.

## Non-Goals
- Do not persist Kilo VFS editor tabs yet.
- Do not migrate old launch-scoped paths unless a concrete persisted-state need appears; stale old paths can fail to decode or be ignored because VFS history persistence is currently disabled.
- Do not add session UI or marketplace VFS kinds in this fix; only make the VFS identity model ready for them.

## Verification
- From `packages/kilo-jetbrains/`, run `./gradlew test --tests ai.kilocode.client.vfs.KiloVfsManagerTest --tests ai.kilocode.client.vfs.KiloVirtualFileSystemTest --tests ai.kilocode.client.session.ui.attachment.AttachmentEditorKindTest`.
- From `packages/kilo-jetbrains/`, run `./gradlew typecheck`.
- If the targeted Gradle test selector is not accepted, run the nearest frontend test task or `./gradlew test` from `packages/kilo-jetbrains/`.
