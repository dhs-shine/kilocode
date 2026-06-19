# Implementation plan — remove internal IntelliJ APIs from the JetBrains prompt completion

Superseded note: Task 1 was intentionally not followed; `CodeCompletionHandlerBase` is retained for split-mode completion behavior. See `basalt-dirt-pr-review-v2.md` and `basalt-dirt-followup-fixes.md` for the current plan.

Audience: an executing model that should follow these steps literally. Read-only research is already
done; the exact edits, rationale, and verification are below. Do **not** expand scope beyond the three
tasks. All paths are under `packages/kilo-jetbrains/`.

## Goal

Eliminate genuinely implementation-only IntelliJ APIs from the prompt slash/mention completion feature,
and bring the remaining unstable-but-public usage into AGENTS compliance (warn + isolate + degrade
gracefully). Keep behavior and all existing tests green.

## Background (verified against `$INTELLIJ_REPO`, do not re-research)

- `CodeCompletionHandlerBase` — `lang-impl`, implementation. Public replacement: perform the standard
  `IdeActions.ACTION_CODE_COMPLETION` action against the editor (this is what `myFixture.completeBasic()`
  does under the hood; runs synchronously in unit-test mode).
- `com.intellij.codeInsight.lookup.impl.LookupImpl` — `impl` package. Public super-interface
  `com.intellij.codeInsight.lookup.LookupEx` (`analysis-api`) declares `getEditor()`,
  `getPresentation()`, and `setPresentation(LookupPresentation)`.
- `LookupPresentation` / `LookupPositionStrategy` — public but `@ApiStatus.Experimental`. **Keep** (no
  non-experimental way to force the lookup above the caret); just add a one-line warning comment.
- Backend `GotoFileModel`, `ChooseByNameViewModel`, `ChooseByNameInScopeItemProvider`,
  `ChooseByNameWeightedItemProvider.filterElementsWithWeights`, `ChooseByNamePopup.getTransformedPattern`,
  `FindSymbolParameters.wrap` — all plain `public` members in `lang-impl`. **None are `@Internal` or
  `@Experimental`.** This is the proven Go-to-File / Search Everywhere engine. **Keep it**, do not
  reimplement; harden with `@Suppress` + comment + a `LinkageError` fallback.

## Scope

- In scope: `frontend/.../session/ui/prompt/PromptPanel.kt`,
  `backend/.../rpc/KiloWorkspaceRpcApiImpl.kt`.
- Out of scope (do **not** touch in this task): the larger refactor that deletes `triggerCompletion`/
  the custom completion-shortcut action (would require rewriting `PromptPanelTest`), the model-package
  moves, the `Dispatchers.IO`/cache items, and any test files. No test edits are required by this plan.

---

## Task 1 — Frontend: replace `CodeCompletionHandlerBase` with the public completion action

File: `frontend/src/main/kotlin/ai/kilocode/client/session/ui/prompt/PromptPanel.kt`

The custom action `COMPLETION_ACTION_TEXT = "Kilo Prompt Completion"`, `triggerCompletion`, and the
shortcut machinery all stay (tests depend on them). Only the body of `showCompletion` changes.

1.1 Rewrite `showCompletion` (currently ~lines 550-554):

```kotlin
    private fun showCompletion(ed: com.intellij.openapi.editor.Editor) {
        // Uses IntelliJ impl/internal completion APIs; revisit on platform upgrades.
        CodeCompletionHandlerBase.createHandler(CompletionType.BASIC, true, false, true)
            .invokeCompletion(project, ed, 1)
    }
```

to:

```kotlin
    private fun showCompletion(ed: com.intellij.openapi.editor.Editor) {
        // Trigger the standard public code-completion action against this editor instead of the
        // impl-only CodeCompletionHandlerBase. Runs synchronously in unit-test mode.
        val action = ActionManager.getInstance().getAction(IdeActions.ACTION_CODE_COMPLETION) ?: return
        val ctx = DataContext { id ->
            when (id) {
                CommonDataKeys.EDITOR.name -> ed
                CommonDataKeys.CARET.name -> ed.caretModel.currentCaret
                CommonDataKeys.PROJECT.name -> project
                else -> null
            }
        }
        val event = AnActionEvent.createEvent(action, ctx, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)
        ActionUtil.updateAction(action, event)
        ActionUtil.performAction(action, event)
    }
```

Rationale for the explicit `DataContext` lambda (not `DataManager.getDataContext`): it deterministically
supplies EDITOR/CARET/PROJECT and mirrors exactly the context the existing `PromptPanelTest` helpers use,
so the headless completion tests stay reliable.

1.2 Imports — remove the two now-unused completion imports, add the two data-context imports:

- Remove `import com.intellij.codeInsight.completion.CodeCompletionHandlerBase`
- Remove `import com.intellij.codeInsight.completion.CompletionType`
- Add `import com.intellij.openapi.actionSystem.CommonDataKeys`
- Add `import com.intellij.openapi.actionSystem.DataContext`

Keep all other imports (`ActionManager`, `ActionUtil`, `AnActionEvent`, `ActionPlaces`, `ActionUiKind`,
`IdeActions`, `DataManager`, `DumbAwareAction`, `AnAction` are still used elsewhere in the file).

---

## Task 2 — Frontend: replace `LookupImpl` with public `LookupEx`

File: `frontend/src/main/kotlin/ai/kilocode/client/session/ui/prompt/PromptPanel.kt`

2.1 In `bindLookup` (currently ~lines 715-727), change the cast and add the experimental-API note:

```kotlin
        connection.subscribe(LookupManagerListener.TOPIC, LookupManagerListener { _, next ->
            val lookup = next as? LookupImpl ?: return@LookupManagerListener
            if (lookup.editor !== editor.getEditor(false)) return@LookupManagerListener
            lookup.presentation = LookupPresentation.Builder(lookup.presentation)
                .withPositionStrategy(LookupPositionStrategy.ONLY_ABOVE)
                .build()
        })
```

to:

```kotlin
        connection.subscribe(LookupManagerListener.TOPIC, LookupManagerListener { _, next ->
            // LookupPresentation/LookupPositionStrategy are @ApiStatus.Experimental (no stable API
            // forces the lookup above the caret). LookupEx is the public lookup interface.
            val lookup = next as? LookupEx ?: return@LookupManagerListener
            if (lookup.editor !== editor.getEditor(false)) return@LookupManagerListener
            lookup.presentation = LookupPresentation.Builder(lookup.presentation)
                .withPositionStrategy(LookupPositionStrategy.ONLY_ABOVE)
                .build()
        })
```

2.2 Imports:

- Remove `import com.intellij.codeInsight.lookup.impl.LookupImpl`
- Add `import com.intellij.codeInsight.lookup.LookupEx`
- Keep `LookupManagerListener`, `LookupPresentation`, `LookupPositionStrategy` imports.

Note: `PromptPanelTest` still references `LookupImpl` on the test side (`acceptLookup`, the
positioned-above-caret assertion). That is fine — tests may inspect the impl; do **not** change them.

---

## Task 3 — Backend: keep the proven Go-to-File engine, harden it

File: `backend/src/main/kotlin/ai/kilocode/backend/rpc/KiloWorkspaceRpcApiImpl.kt`

3.1 Annotate the `search(...)` adapter (currently starts ~line 316) and document the unstable-API reliance:

```kotlin
    private fun search(project: Project, base: Path, query: String, limit: Int): List<WorkspaceFileDto> {
```

to:

```kotlin
    // Uses the IDE Go-to-File engine (com.intellij.ide.util.gotoByName.*). These are public but
    // unstable lang-impl classes (not @ApiStatus.Internal) — the same engine behind Search Everywhere,
    // chosen for proven large-repo performance. searchFiles() degrades gracefully on LinkageError.
    @Suppress("UnstableApiUsage")
    private fun search(project: Project, base: Path, query: String, limit: Int): List<WorkspaceFileDto> {
```

(If the DevKit inspection still flags the import statements at file scope after this, fall back to a
file-level `@file:Suppress("UnstableApiUsage")` at the top of the file. Prefer the function-level form.)

3.2 Add a `LinkageError` fallback in `searchFiles` (currently ~lines 194-205) so an API drift in a future
platform degrades to "no suggestions" instead of crashing the prompt. Change:

```kotlin
        return try {
            val files = readAction { search(project, base, query, limit.coerceIn(1, 200)) }
            FileSearchResultDto(files = files, git = git)
        } catch (e: IndexNotReadyException) {
            FileSearchResultDto(indexing = true, git = git)
        }
```

to:

```kotlin
        return try {
            val files = readAction { search(project, base, query, limit.coerceIn(1, 200)) }
            FileSearchResultDto(files = files, git = git)
        } catch (e: IndexNotReadyException) {
            FileSearchResultDto(indexing = true, git = git)
        } catch (e: LinkageError) {
            LOG.warn("file search API unavailable; returning no suggestions", e)
            FileSearchResultDto(git = git)
        }
```

No new imports needed (`LinkageError` is `java.lang`; `LOG` already exists in the class).

---

## Verification (run from `packages/kilo-jetbrains/`, requires Java 21)

1. Typecheck: `./gradlew typecheck` (or `bun run typecheck`).
2. Frontend tests (must pass unchanged):
   - `./gradlew test --tests "ai.kilocode.client.session.ui.PromptPanelTest"`
   - `./gradlew test --tests "ai.kilocode.client.session.ui.prompt.KiloPromptCompletionProviderTest"`
   - In particular these must stay green: `test prompt local completion shortcut opens mention lookup`,
     `test prompt local completion shortcut opens slash lookup`, `test prompt completion lookup is
     positioned above caret`, `test accepted file mention highlights immediately`.
3. Manual (run IDE): typing `@` and a leading `/` opens the popup; Ctrl+Space opens it; the popup renders
   above the caret. Open DevKit → `Frontend and Backend API Usage` (and `Unstable API Usage`): the
   frontend completion/lookup usages should no longer be flagged; the backend `search` usages are
   acknowledged via `@Suppress` + comment.

## Acceptance criteria

- `PromptPanel.kt` (production) contains no references to `CodeCompletionHandlerBase`, completion
  `CompletionType`, or `com.intellij.codeInsight.lookup.impl.LookupImpl`.
- `bindLookup` casts to `LookupEx`; lookup positioning behavior unchanged.
- `KiloWorkspaceRpcApiImpl.search` is `@Suppress("UnstableApiUsage")` with the explanatory comment;
  `searchFiles` catches `LinkageError` and degrades.
- Typecheck and the listed tests pass; no test files were modified.

## Risk + fallback

- Primary risk: performing `ACTION_CODE_COMPLETION` via the `DataContext` lambda does not open the
  lookup in the headless `PromptPanelTest` (e.g. the action's `update()` disables itself).
- Fallback (only if Task 1 tests fail): keep the action approach but additionally force autopopup —
  in the editor settings provider (`addSettingsProvider { ed -> ... }`) add
  `ed.putUserData(com.intellij.codeInsight.AutoPopupController.ALWAYS_AUTO_POPUP, true)`, and implement
  `showCompletion` as `com.intellij.codeInsight.AutoPopupController.getInstance(project).scheduleAutoPopup(ed)`.
  `ALWAYS_AUTO_POPUP` is a public `Key` and `scheduleAutoPopup(Editor)` is public (this is exactly how the
  platform's `TextFieldWithCompletion(forceAutoPopup = true)` works). This is async, so rely on the
  tests' existing `waitForLookupItems` polling.
