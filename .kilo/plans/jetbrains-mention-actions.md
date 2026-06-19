# JetBrains Mention Actions Organization Plan

## Goal

Organize known JetBrains prompt mentions the same way as built-in slash actions, so built-in mention names and metadata are centralized instead of hardcoded across completion, highlighting, and prompt part conversion.

## Current Findings

- There is one known built-in mention today: `@git-changes`.
- File mentions are dynamic backend search results from `KiloWorkspaceService.searchFiles(...)`; they are not built-in mention actions and should stay dynamic.
- `KiloPromptCompletionProvider` hardcodes `git-changes` in three places:
  - Completion availability and lookup insertion when `FileSearchResultDto.git` is true.
  - Highlighting as a valid mention without requiring a tracked file path.
  - Validation skip so it is not resolved as a file path.
- `PromptMentionParts.kt` hardcodes the same token, output filename, and resource URI in `gitChangesPart(...)`.
- `SessionUi.mentionParts(...)` hardcodes `@git-changes` before calling `gitChangesPart(...)`.
- Tests assert `git-changes` and `@git-changes` literals in completion, highlighting, prompt panel, and prompt part conversion tests.

## Relevant Files

- `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/ui/prompt/KiloPromptCompletionProvider.kt`
  - Known mention completion, highlighting, validation skip, and lookup element construction.
- `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/ui/prompt/PromptMentionParts.kt`
  - Converts `@git-changes` into a synthetic text attachment from the git diff.
- `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/SessionUi.kt`
  - Detects the git changes mention before fetching git diff.
- `packages/kilo-jetbrains/frontend/src/main/resources/messages/KiloBundle.properties`
  - Existing localization key `prompt.mention.gitChanges`.
- `packages/kilo-jetbrains/frontend/src/test/kotlin/ai/kilocode/client/session/ui/prompt/KiloPromptCompletionProviderTest.kt`
  - Completion and highlighting tests for `git-changes`.
- `packages/kilo-jetbrains/frontend/src/test/kotlin/ai/kilocode/client/session/ui/prompt/PromptMentionPartsTest.kt`
  - Prompt part conversion tests for `@git-changes`.
- `packages/kilo-jetbrains/frontend/src/test/kotlin/ai/kilocode/client/session/ui/PromptPanelTest.kt`
  - Editor highlighting and highlighter lifecycle tests involving `@git-changes`.

## Implementation Plan

1. Add a top-level `MentionAction` class in the prompt package:
   - Path: `frontend/src/main/kotlin/ai/kilocode/client/session/ui/prompt/MentionAction.kt`
   - Package: `ai.kilocode.client.session.ui.prompt`
   - Shape should mirror `SlashAction` where practical, but without an executable callback:
     - `name`
     - `description`
     - `hints`
     - `available(search: FileSearchResultDto): Boolean` or equivalent, so availability rules live with known mention metadata
   - Add an inner `Spec` class for built-in metadata:
     - `name`
     - `descriptionKey`
     - `hints`
     - `filename` for resource-backed prompt parts where applicable
     - `uri` for resource-backed prompt parts where applicable
     - `available(search: FileSearchResultDto): Boolean` for completion visibility
   - Add `GIT_CHANGES` and ordered `ALL` constants.
   - Add small token helpers if useful, for example `Spec.token` returning `@name`, to avoid reconstructing token literals in multiple files.

2. Bind mention specs where localized descriptions are needed:
   - Add a small binder close to completion construction, analogous to slash action binding, or inside `KiloPromptCompletionProvider` if that keeps the constructor simpler.
   - Prefer passing `mentions: List<MentionAction>` into `KiloPromptCompletionProvider` if this keeps known mention completion explicit and testable.
   - Keep `KiloBundle.message(...)` resolution in frontend/UI code, not in static metadata.

3. Update `KiloPromptCompletionProvider` to use known mention actions:
   - Add a `mentions` constructor parameter if using explicit binding.
   - Replace hardcoded highlight checks with a known mention name set.
   - Replace validation skip literal checks with the known mention name set.
   - Replace completion logic for `git-changes` with iteration over known mentions whose names or hints match the prefix and whose `available(search)` condition is satisfied.
   - Preserve current availability behavior: show `git-changes` only when `search.git` is true.
   - Preserve ordering: known mentions before file results, with the existing priority behavior so `git-changes` remains first for blank mention completion when available.
   - Preserve no-match behavior: no placeholder if a known mention matches; otherwise placeholder only when there are no matching known mentions and no file results.

4. Update prompt part conversion to use centralized metadata:
   - Replace `"@git-changes"`, `"git-changes"`, and `"git-changes.txt"` in `PromptMentionParts.kt` with `MentionAction.GIT_CHANGES` metadata.
   - Keep the function name `gitChangesPart(...)` because the implementation still specifically converts git diff text.
   - Preserve the boundary check that ignores `@git-changes-foo`.

5. Update `SessionUi.mentionParts(...)`:
   - Replace `text.contains("@git-changes")` with the centralized token from `MentionAction.GIT_CHANGES`.
   - Keep diff fetching lazy and only when the prompt contains the known mention token.

6. Update tests:
   - Replace hardcoded built-in mention names with `MentionAction.GIT_CHANGES.name` or `MentionAction.GIT_CHANGES.token` where practical.
   - Keep test strings readable where the full prompt text is the important fixture, but derive expected values from `MentionAction` to reduce duplicated metadata.
   - Add or adjust one provider test to assert known mention metadata is used for completion description if there is an existing practical assertion path; otherwise do not add brittle UI presentation assertions.

## Non-Goals

- Do not turn dynamic file mentions into `MentionAction` entries.
- Do not add new built-in mentions.
- Do not change `@git-changes` behavior, availability, inserted text, highlighting, validation, prompt part source metadata, or output filename.
- Do not rename `gitChangesPart(...)` unless the implementation later supports multiple resource mention conversions.

## Verification

- Run targeted frontend tests from `packages/kilo-jetbrains`:
  - `./gradlew :frontend:test --tests "ai.kilocode.client.session.ui.prompt.KiloPromptCompletionProviderTest" --tests "ai.kilocode.client.session.ui.prompt.PromptMentionPartsTest" --tests "ai.kilocode.client.session.ui.PromptPanelTest"`
- Run JetBrains typecheck from `packages/kilo-jetbrains`:
  - `./gradlew typecheck`
