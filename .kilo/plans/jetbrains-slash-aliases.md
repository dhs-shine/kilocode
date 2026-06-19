# Add JetBrains Slash Command Aliases

## Goal
Add the VS Code slash-command alias parity that JetBrains is missing, and make those aliases execute the same client-side actions when typed and sent directly.

## Current Findings
- VS Code aliases from `packages/kilo-vscode/webview-ui/src/hooks/useSlashCommand.ts`:
  - `/clear` -> `/new`
  - `/continue`, `/history`, `/resume` -> `/sessions`
  - `/variants`, `/reasoning`, `/thinking` -> `/variant`
  - `/smol`, `/condense` -> `/compact`
- JetBrains aliases currently in `SlashAction.kt`:
  - `/history`, `/resume` -> `/sessions`
  - `/modes` -> `/agents`
  - `/reasoning` -> `/variant`
  - `/smol` -> `/compact`
- Missing JetBrains aliases:
  - `/clear`
  - `/continue`
  - `/variants`
  - `/thinking`
  - `/condense`
- `KiloPromptCompletionProvider` already uses `hints` as lookup strings and search matches, so adding missing hints improves autocomplete discovery.
- Direct send handling currently uses `serverCommand(text)` and only excludes canonical client names via `clientNames()`. It does not resolve client aliases, so manually sending `/clear` would fall through to a normal prompt unless selected from autocomplete.

## Implementation Plan
1. Update `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/ui/prompt/SlashAction.kt`:
   - Add `clear` to `NEW.hints`.
   - Add `continue` to `SESSIONS.hints` while preserving `history` and `resume`.
   - Add `variants` and `thinking` to `VARIANT.hints` while preserving `reasoning`.
   - Add `condense` to `COMPACT.hints` while preserving `smol`.

2. Update `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/ui/prompt/KiloPromptCompletionProvider.kt`:
   - Add a small client-action resolver, for example `fun clientAction(text: String): SlashAction?`.
   - Parse only leading slash commands the same way `serverCommand(text)` does.
   - Match the parsed name against each action's canonical `name` and `hints`.
   - Return `null` for blank names and non-leading slash text.
   - Update `clientNames()` or add a separate `clientTokens()` helper so `serverCommand(text)` also treats aliases as client-owned and does not route an alias to a server command with the same name.
   - Update `highlights(text)` to highlight aliases as valid commands, not just canonical client command names.

3. Update `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/SessionUi.kt`:
   - In `sendPrompt`, after clearing the prompt and before server-command dispatch, call the new client-action resolver.
   - If a client action is found, execute `action.action()` and return without calling `controller.prompt(...)` or `controller.command(...)`.
   - Keep scroll follow behavior consistent with the current send flow.
   - This will also make directly typed canonical commands such as `/new` execute client actions, which aligns with the alias behavior and avoids sending client commands as ordinary prompts.

4. Add or update tests in `packages/kilo-jetbrains/frontend/src/test/kotlin/ai/kilocode/client/session/ui/prompt/KiloPromptCompletionProviderTest.kt`:
   - Construct test actions with representative hints, including `clear` and `continue`.
   - Assert slash completion finds a command by alias, e.g. `/cle<caret>` shows/selects `new`.
   - Assert `clientAction("/clear")` resolves to the `new` action.
   - Assert `clientAction("/continue")` resolves to the `sessions` action.
   - Assert `clientAction("hi /clear")` is `null`.
   - Assert `serverCommand("/clear")` is `null` even if a server command named `clear` exists.
   - Assert `highlights("/clear")` marks the alias as `COMMAND`.

5. Add a focused `SessionUi` behavior test if an existing harness makes it straightforward:
   - Verify sending `/clear` triggers `manager.newSession()` and does not call `controller.prompt`.
   - If the harness would require heavy UI plumbing, rely on provider resolver tests plus typecheck and avoid brittle UI tests.

6. Add a changeset:
   - Package: `@kilocode/kilo-jetbrains`
   - Bump: `patch`
   - Suggested text: `Support VS Code slash-command aliases in the JetBrains prompt.`

## Verification
Run the smallest relevant checks from `packages/kilo-jetbrains/`:
- `./gradlew test --tests ai.kilocode.client.session.ui.prompt.KiloPromptCompletionProviderTest`
- `bun run typecheck`

If the focused Gradle test filter is not accepted by the repo setup, run `./gradlew test` from `packages/kilo-jetbrains/` instead.

## Non-Goals
- Do not add `/export` or `/remote` in this change.
- Do not change server-side CLI slash commands.
- Do not introduce new RPC, backend services, or UI surfaces.
