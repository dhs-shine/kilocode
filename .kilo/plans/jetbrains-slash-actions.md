# JetBrains Slash Actions Organization Plan

## Goal

Organize JetBrains built-in slash actions so names and metadata are centralized instead of scattered across `SessionUi` and tests.

## Current Findings

- Built-in client slash actions are currently defined inline in `frontend/src/main/kotlin/ai/kilocode/client/session/SessionUi.kt` inside `slashActions()`.
- `SlashAction` is currently a nested data class inside `KiloPromptCompletionProvider`.
- Tests instantiate `KiloPromptCompletionProvider.SlashAction("new", ...)` directly in:
  - `frontend/src/test/kotlin/ai/kilocode/client/session/ui/prompt/KiloPromptCompletionProviderTest.kt`
  - `frontend/src/test/kotlin/ai/kilocode/client/session/ui/PromptPanelTest.kt`
- I found no `git-review` literal in `packages/kilo-jetbrains`. The prompt package does contain hardcoded `git-changes`, but that is an `@mention` resource, not a slash action.

## Implementation Plan

1. Add a top-level `SlashAction` class in the prompt package:
   - Path: `frontend/src/main/kotlin/ai/kilocode/client/session/ui/prompt/SlashAction.kt`
   - Package: `ai.kilocode.client.session.ui.prompt`
   - Move the current data shape out of `KiloPromptCompletionProvider`.

2. Model built-in actions as named specs inside `SlashAction`:
   - Add an inner `Spec` data class or equivalent to hold `name`, `descriptionKey`, and `hints`.
   - Add named constants for each built-in action:
     - `NEW`
     - `SESSIONS`
     - `MODELS`
     - `AGENTS`
     - `VARIANT`
     - `COMPACT`
     - `SETTINGS`
     - `HELP`
   - Add an ordered `ALL` list containing all specs.

3. Keep execution binding in `SessionUi`:
   - Replace inline string literals like `"new"`, `"sessions"`, etc. with the named `SlashAction` specs.
   - Build executable `SlashAction` instances by binding each spec to its handler lambda.
   - Keep the current behavior and ordering unchanged.

4. Update `KiloPromptCompletionProvider` to use the top-level class:
   - Remove the nested `data class SlashAction`.
   - Constructor remains `actions: List<SlashAction>`.
   - Existing provider logic can continue to use `action.name`, `action.description`, `action.hints`, and `action.action`.

5. Update tests to refer to centralized names:
   - Replace `KiloPromptCompletionProvider.SlashAction` with `SlashAction`.
   - Replace built-in hardcoded `"new"` references with `SlashAction.NEW.name` or helper-bound `SlashAction.NEW` where practical.
   - Leave synthetic non-built-in test commands like `"next"` as custom test actions unless a test-only helper is clearer.

6. Keep localization unchanged:
   - Continue using existing `prompt.slash.*` keys in `KiloBundle.properties`.
   - `SlashAction` specs can store the message key; `SessionUi` can resolve descriptions when binding specs.

7. Verification:
   - Run JetBrains typecheck from `packages/kilo-jetbrains`: `./gradlew typecheck`.
   - Run targeted frontend tests if available through Gradle filtering for `KiloPromptCompletionProviderTest` and `PromptPanelTest`; otherwise run the smallest JetBrains test task that covers frontend tests.

## Non-Goals

- Do not change server/config command handling through `CommandDto`.
- Do not change `@git-changes` mention behavior unless explicitly requested; it is not a slash action.
- Do not add new built-in slash actions or change aliases/descriptions.
