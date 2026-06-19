# JetBrains prompt completion follow-up: cancellation noise and explicit shortcut

## Goal

Finish the JetBrains prompt completion fix by addressing the two issues reported after the first implementation pass:

1. Typing `@...` rapidly logs repeated `workspace file search failed ... IndicatorCancellationException` warnings.
2. Pressing the code-completion shortcut (`Control-Space` in the user's keymap) in the prompt editor does not reliably invoke slash/mention completion.

The existing no-match placeholder, fast-typing lookup retention, and mid-token token detection changes stay in place.

## Current state

Already implemented in Kilo-owned JetBrains files:

- `KiloPromptCompletionProvider` adds a non-destructive `No matches` info row for slash and mention no-results.
- Slash and mention result sets call `restartCompletionOnAnyPrefixChange()`.
- Mention indexing row uses the same no-op info-row behavior.
- `PromptPanel` sets `AutoPopupController.ALWAYS_AUTO_POPUP` on the prompt editor.
- `token()` now recognizes the full whitespace-bounded token around the caret, while `prefix` remains the text before the caret.
- Tests cover no-match placeholders, accepting placeholders, real matches, and mid-token explicit completion through the provider fixture.

## New findings

### Cancellation warnings while typing

The warning stack is expected IntelliJ completion cancellation, not a real backend failure:

- `KiloPromptCompletionProvider.search()` runs `service.searchFiles(...)` inside `runBlockingCancellable` during completion calculation.
- IntelliJ cancels stale completion work on each prefix change while the user types.
- IntelliJ source confirms `IndicatorCancellationException` extends `java.util.concurrent.CancellationException`, and `ProcessCanceledException` extends Kotlin/JVM `CancellationException`.
- `KiloWorkspaceService.searchFiles()` currently catches broad `Exception`, logs it as a warning, and returns `FileSearchResultDto()`.
- IntelliJ source explicitly says `ProcessCanceledException` should not be logged or swallowed.

Conclusion: `searchFiles()` must not log completion/progress cancellation. It should rethrow cancellation before the generic failure catch so the completion infrastructure can discard stale results.

### `Control-Space` in the prompt editor

Provider tests prove `TextCompletionContributor` can invoke `KiloPromptCompletionProvider`, but the real prompt editor shortcut path can diverge:

- `EditorTextField` and its content component already publish `CommonDataKeys.EDITOR`, so editor data context is probably not the primary issue.
- IntelliJ `BaseCodeCompletionAction` uses `ActionRemoteBehavior.FrontendOtherwiseBackend`.
- In remote/split frontend mode, completion can fall back to backend execution or use frontend RD completion filtering.
- IntelliJ `CompletionContributor.forLanguage()` filters contributors to `FrontendCompletionContributor` when RD frontend completion is enabled; platform `TextCompletionContributor` is not marked as such.
- Our prompt completion provider is frontend UI state installed through `TextCompletionUtil`, so relying on the global code-completion action is fragile in split/RD mode.

Conclusion: keep using the existing prompt-local `showCompletion(editor)` path, but bind the active code-completion shortcut directly to it for the prompt editor component. This keeps the behavior frontend-local and avoids the RD contributor/action fallback path.

## Implementation plan

### 1. Stop logging stale completion cancellation

File: `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/app/KiloWorkspaceService.kt`

- In `searchFiles(directory, query, limit)`, add a cancellation catch before the generic catch:

```kotlin
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    LOG.warn(...)
    FileSearchResultDto()
}
```

- Use the JVM/Kotlin cancellation type that catches both IntelliJ `IndicatorCancellationException` and `ProcessCanceledException` on this target.
- Do not add logging for cancellation.
- Leave non-cancellation failures unchanged: still warn and return `FileSearchResultDto()`.
- Do not catch cancellation inside `KiloPromptCompletionProvider.search()`; let completion infrastructure cancel stale calculations and avoid caching a cancelled result.

Tests:

- Add `KiloWorkspaceServiceTest` coverage where `FakeWorkspaceRpcApi.search` throws `CancellationException` and `service.searchFiles(...)` rethrows it.
- Keep existing behavior testable for non-cancellation failures if a small fake failure case is easy to add, but do not expand the scope unnecessarily.

### 2. Bind explicit completion shortcut locally in `PromptPanel`

File: `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/ui/prompt/PromptPanel.kt`

- Add a prompt-local action field, for example a private `AnAction?`, that calls `showCompletion(editor)` for the initialized prompt editor.
- When the editor is configured in `addSettingsProvider`, install this action on `ed.contentComponent` using the current shortcut set from `IdeActions.ACTION_CODE_COMPLETION`:

```kotlin
val base = ActionManager.getInstance().getAction(IdeActions.ACTION_CODE_COMPLETION)
completionAction.registerCustomShortcutSet(base.shortcutSet, ed.contentComponent)
```

- Use `DumbAwareAction.create { ... }` or a small private action object; keep it local to `PromptPanel`.
- Only install this action when `completion != null`.
- Unregister the action in `removeNotify()` with `unregisterCustomShortcutSet(ed.contentComponent)` to avoid retaining disposed editor components.
- On keymap changes for `IdeActions.ACTION_CODE_COMPLETION`, unregister and reinstall so custom keymaps are respected. The existing `bindKeymap()` listener already watches shortcut changes; extend that path.
- Keep the existing document-listener trigger for inserted `@` and leading `/`, because native auto-popup still does not fire for those trigger chars.
- Keep `showCompletion()` unchanged for now, including the `ONLY_ABOVE` lookup positioning. It already uses the frontend-local handler path the manual `@`/`/` trigger relies on.

Tests:

- Add `PromptPanelTest` coverage with a real `PromptPanel` plus `KiloPromptCompletionProvider`:
  - Realize the panel.
  - Set the prompt editor text to `@dep` and place the caret at the end.
  - Configure `rpc.searchResult` with `src/deploy.ts`.
  - Invoke the registered prompt-local completion action and assert the active lookup contains `src/deploy.ts`.
  - Repeat or add a slash case for `/ne` showing `new`.
- Prefer invoking the registered action via `ActionUtil.getActions(editor.contentComponent)` and an `AnActionEvent` built from the editor component data context. This verifies the component-local shortcut binding without depending on brittle headless key-event dispatch.
- If action ordering produces multiple registered actions, identify the prompt-local action by shortcut set and by invoking only actions registered on the editor content component after `PromptPanel` initialization.

### 3. Re-run focused verification

From `packages/kilo-jetbrains/`:

- `./gradlew :frontend:test --tests ai.kilocode.client.session.ui.prompt.KiloPromptCompletionProviderTest`
- `./gradlew :frontend:test --tests ai.kilocode.client.session.ui.PromptPanelTest`
- `./gradlew :frontend:test --tests ai.kilocode.client.app.KiloWorkspaceServiceTest`
- `./gradlew typecheck`

Known pre-existing caveat:

- Full `./gradlew test` was previously failing with unrelated frontend `NoSuchMethodError` failures in existing UI constructor tests. Do not treat that as introduced by this completion fix unless it changes shape after these edits.

## Manual verification

In a sandbox IDE:

- Type `@d`, `@de`, `@dep` quickly. No `workspace file search failed ... IndicatorCancellationException` warnings should appear.
- Type an unmatched mention such as `@zzzz`; the popup should stay open with `No matches`.
- Put the caret in or after `@dep` and press the code-completion shortcut; mention completions should open.
- Type `/ne` and press the code-completion shortcut; slash completions should open.
- Confirm the popup still appears above the prompt editor for manual `@` and leading `/` triggers.

## Scope and constraints

- Touch only Kilo-owned JetBrains plugin files; no `kilocode_change` markers are needed.
- Do not introduce a new global completion contributor unless the prompt-local shortcut binding proves insufficient.
- Do not rewrite completion UI into a custom popup for this pass.
- Avoid swallowing IntelliJ cancellation exceptions; cancellation is control flow, not a user-visible failure.
