# JetBrains Settings List Renderer Layout Plan

## Goal
Fix shared JetBrains settings-list rendering so each settings page can choose the correct row-height behavior and row text spacing:

- Providers use each row's own preferred height.
- Agents, MCPs, skills, and workflows keep equal row heights.
- Row title/header text has no extra renderer padding.
- Row descriptions, when present, get a slight left padding so they read as secondary text under the title.
- MCP rows stay visually aligned with agent rows.

## Current Context
- Shared list rendering lives in `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/settings/base/`:
  - `SettingsListView.kt`
  - `SettingsListRenderer.kt`
  - `SettingsListModel.kt`
  - `SettingsListPanel.kt`
- `SettingsListView.syncCellHeight(...)` currently always computes the tallest rendered row and assigns it to `JBList.fixedCellHeight`.
- This equal-height behavior works for agents/MCP-style lists but makes provider rows overly tall because providers have sections, icons, descriptions, and selected action cells.
- Providers currently use `SettingsListView` through `ProvidersContent` in `ProvidersSettingsUi.kt`, so they inherit equal-height behavior unintentionally.
- Agent Behavior pages use `SettingsListPanel`, so base-class configuration is the right place to preserve equal heights consistently.

## Decisions
1. Add an explicit shared settings-list layout/config model in base settings list code.
2. Make row-height behavior configurable:
   - Equal tallest row: current behavior; used by agents, MCPs, skills, workflows.
   - Preferred row height: set `fixedCellHeight = -1`; used by providers.
3. Make text inset behavior configurable in the shared renderer:
   - Title/header line gets no extra renderer padding.
   - Description line gets a small left padding only when visible.
4. Configure every settings-list consumer explicitly instead of relying on hidden defaults.
5. Keep changes in base settings classes plus page configuration only; do not change individual row data to fake layout.

## Implementation Tasks
1. Update `SettingsListModel.kt` or a nearby base file with shared list layout types.
   - Add an enum/sealed type for row height policy, for example `SettingsListRowHeight.Equal` and `SettingsListRowHeight.Preferred`.
   - Add a small config data class, for example `SettingsListConfig`, containing row height policy and row text spacing options.
   - Keep names short and consistent with repo style.
2. Update `SettingsListView.kt`.
   - Accept a `SettingsListConfig` constructor parameter.
   - Pass the config to `SettingsListRenderer`.
   - In `syncCellHeight(rows)`, preserve current max-height calculation for equal-height mode.
   - In preferred-height mode, set `list.fixedCellHeight = -1` and revalidate only if it changed.
   - Keep recalculation on `update(...)` and `filter(...)`.
   - Ensure action hit testing still uses actual `getCellBounds(...)` from the list.
3. Update `SettingsListRenderer.kt`.
   - Accept the shared config.
   - Remove shared internal left padding from the title/header area.
   - Keep only the real icon-to-title gap when an icon exists.
   - Apply slight left padding to the description component only when description text is visible.
   - Preserve right-side action alignment and badge behavior.
   - Avoid hardcoded raw Swing dimensions where `UiStyle.Gap` or `JBUI` helpers apply.
4. Update `SettingsListPanel.kt`.
   - Accept/pass a list config into its internal `SettingsListView`.
   - Default can remain equal-height for safety, but all current subclasses should pass the intended config explicitly.
5. Configure all consumers.
   - `ProvidersContent` in `ProvidersSettingsUi.kt`: use preferred row height and the shared row text spacing.
   - `AgentsSettingsUi` in `AgentsConfigurable.kt`: use equal row height.
   - `McpSettingsUi` in `McpConfigurable.kt`: use equal row height and same base layout as agents.
   - `SkillsSettingsUi` in `SkillsConfigurable.kt`: use equal row height.
   - `WorkflowsSettingsUi` in `WorkflowsConfigurable.kt`: use equal row height.
6. Avoid unrelated changes.
   - Do not touch unrelated unstaged agent import work or localized bundles unless tests/typecheck require a direct import/string fix.
   - Do not rewrite provider logic, MCP loading, or agent behavior data flow.

## Testing Tasks
1. Update `SettingsListViewTest.kt`.
   - Keep existing tests proving equal-height behavior remains the default or explicit equal mode.
   - Add a preferred-height test where a row with description is taller than a plain row.
   - Assert preferred-height mode leaves `fixedCellHeight == -1`.
   - Add/adjust a test proving filtering respects the configured row-height mode.
2. Add renderer spacing coverage in base tests where practical.
   - Render a row with title and description.
   - Assert title starts without extra renderer left padding relative to the content area.
   - Assert description has a small left offset only when visible.
3. Update `ProvidersSettingsUiTest.kt`.
   - Verify provider content/list uses preferred row heights.
   - Use a provider row with description/section and a simpler row, then assert their rendered bounds are not forced equal.
   - Keep existing provider renderer action-label and icon/description tests passing.
4. Update or add focused assertions in `AgentsSettingsUiTest.kt` and `McpSettingsUiTest.kt`.
   - Assert rows with and without descriptions keep equal heights.
   - Assert MCP action/title alignment remains consistent with the agent list behavior.

## Validation
Run focused JetBrains frontend checks from `packages/kilo-jetbrains/`:

```sh
./gradlew :frontend:test --tests ai.kilocode.client.settings.base.SettingsListViewTest --tests ai.kilocode.client.settings.providers.ProvidersSettingsUiTest --tests ai.kilocode.client.settings.agents.AgentsSettingsUiTest --tests ai.kilocode.client.settings.agents.McpSettingsUiTest
./gradlew :frontend:compileKotlin
```

If the Gradle test filter is not accepted by the current setup, run the nearest equivalent `:frontend:test` invocation that includes those test classes.

## Risks And Edge Cases
- Variable provider row heights with selected-only action cells may need `revalidate()` on selection changes if selected actions increase preferred height. Prefer keeping selected action cells within the unselected row height; only add selection revalidation if a test or manual inspection shows clipping.
- Section headers are part of rendered row preferred height. In preferred provider mode, only section-start rows should include that extra height.
- Equal-height lists should continue measuring rows as selected so selected-only action cells do not clip.
- Removing title/header padding should not remove icon-to-title spacing; icon rows still need a visible gap between icon and title.
- Description padding should not reserve space when no description exists.
