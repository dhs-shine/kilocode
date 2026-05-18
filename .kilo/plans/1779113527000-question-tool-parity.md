# Plan: Hide Active Question Tool Row in JetBrains

## Goal

Match VS Code question prompt behavior in the JetBrains session transcript by preventing the temporary empty `Question` running tool card from rendering while the active question form is shown.

The visible result should be:

- No separate empty `Question` / `Running` tool element above the question card while a linked question request is pending.
- The existing `QuestionView` card remains visible in the scrollable session transcript.
- Completed question tool results can still render after the question resolves, matching VS Code's fallback to the completed tool part.

## VS Code Reference

VS Code does not render the pending `question` tool part as a normal tool card when that tool is linked to an active question request.

Relevant files:

- `packages/kilo-vscode/src/kilo-provider-utils.ts`
  - Maps `question.asked` to a webview `questionRequest` and preserves `request.tool`.
- `packages/kilo-vscode/webview-ui/src/context/session.tsx`
  - Stores pending questions as transient UI state.
- `packages/kilo-vscode/webview-ui/src/components/chat/AssistantMessage.tsx`
  - Matches an active `QuestionRequest` by `tool.messageID` and `tool.callID`.
  - Renders `QuestionDock` instead of the regular tool `<Part>` while the question is active.
- `packages/kilo-vscode/webview-ui/src/components/chat/ChatView.tsx`
  - Renders questions without a `tool` reference as standalone bottom transcript prompts.

The parity rule for JetBrains should be the same:

- If the active question has a tool reference, suppress the matching pending/running `question` tool part.
- If the active question has no tool reference, keep the current standalone prompt behavior.
- When the question is no longer active, stop suppressing so the completed tool result can render normally.

## Current JetBrains Gap

JetBrains already receives and stores the question tool reference:

- `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/controller/SessionController.kt`
  - `toQuestion(dto)` maps `dto.tool` to `ToolCallRef(messageID, callID)`.

JetBrains also renders the active question as a transcript footer:

- `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/ui/SessionMessageListPanel.kt`
  - `syncActive(SessionState.AwaitingQuestion)` calls `question?.show(state.question)`.

But assistant message parts are still rendered normally:

- `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/views/MessageView.kt`
  - Builds a view for every non-`StepFinish` part.
- `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/views/ViewFactory.kt`
  - Maps all `Tool` content to `ToolView`.

The model currently drops `PartDto.callID` when converting tool parts, so `MessageView` cannot match a tool part against `Question.tool`.

## Implementation Steps

### 1. Preserve Tool Call IDs

Update `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/model/Message.kt`:

```kotlin
class Tool(id: String, val name: String, var kind: ToolKind) : Content(id) {
    var callId: String? = null
    ...
}
```

Update `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/model/SessionModel.kt`:

- In `updateExisting(...)` for `is Tool`, assign `existing.callId = dto.callID`.
- In `fromDto(...)` for `"tool"`, assign `callId = dto.callID`.

Keep `renderTool(...)` unchanged unless debugging requires adding the call id; avoiding output format churn is preferable.

### 2. Add Tool Suppression to `MessageView`

Update `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/views/MessageView.kt`.

Add imports:

```kotlin
import ai.kilocode.client.session.model.Tool
import ai.kilocode.client.session.model.ToolCallRef
import ai.kilocode.client.session.model.ToolExecState
```

Add state:

```kotlin
private var hidden: ToolCallRef? = null
```

Add a public method for the transcript panel:

```kotlin
fun setHiddenQuestionTool(ref: ToolCallRef?) {
    if (hidden == ref) return
    hidden = ref
    rebuildParts()
}
```

Add a predicate:

```kotlin
private fun hidden(content: Content): Boolean {
    val ref = hidden ?: return false
    if (content !is Tool) return false
    if (content.name != "question") return false
    if (content.state != ToolExecState.PENDING && content.state != ToolExecState.RUNNING) return false
    return msg.info.id == ref.messageId && content.callId == ref.callId
}
```

Update initialization and `upsertPart(content)` so hidden matching content is not added to `parts` or the Swing component tree.

Recommended shape:

```kotlin
private fun addPart(content: Content) {
    if (content is StepFinish) return
    if (hidden(content)) return
    val view = ViewFactory.create(content)
    view.applyStyle(style)
    parts[content.id] = view
    add(view)
}
```

For `upsertPart(content)`:

- If `content is StepFinish`, return.
- If `hidden(content)`:
  - Remove any existing view for `content.id` from `parts` and from the component tree.
  - Refresh and return.
- If the view exists, call `existing.update(content)`.
- Otherwise create and add it.

For `setHiddenQuestionTool(...)`, prefer rebuilding visible parts from `msg.parts` so clearing suppression re-adds the hidden tool in the correct original order:

```kotlin
private fun rebuildParts() {
    removeAll()
    parts.clear()
    for ((_, content) in msg.parts) addPart(content)
    syncBorder()
    refresh()
}
```

This rebuild only happens when the active question tool reference changes, not during normal content updates.

### 3. Propagate Active Question Tool Ref from `SessionMessageListPanel`

Update `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/ui/SessionMessageListPanel.kt`.

Add import:

```kotlin
import ai.kilocode.client.session.model.ToolCallRef
```

Add state:

```kotlin
private var hidden: ToolCallRef? = null
```

When registering a message view, immediately apply the current hidden ref:

```kotlin
private fun register(msgId: String, tv: TurnView, mv: MessageView) {
    msgToTurn[msgId] = tv
    msgToView[msgId] = mv
    mv.setHiddenQuestionTool(hidden)
}
```

Add helper:

```kotlin
private fun setHiddenQuestionTool(ref: ToolCallRef?) {
    if (hidden == ref) return
    hidden = ref
    for (view in msgToView.values) view.setHiddenQuestionTool(ref)
}
```

Update `syncActive(state)`:

```kotlin
private fun syncActive(state: SessionState = model.state) {
    when (state) {
        is SessionState.AwaitingQuestion -> {
            setHiddenQuestionTool(state.question.tool)
            permission?.hideView()
            question?.show(state.question)
        }
        is SessionState.AwaitingPermission -> {
            setHiddenQuestionTool(null)
            question?.hideView()
            permission?.show(state.permission)
        }
        else -> {
            setHiddenQuestionTool(null)
            question?.hideView()
            permission?.hideView()
        }
    }
}
```

This keeps standalone questions (`state.question.tool == null`) unchanged while suppressing only the matched linked question tool row.

### 4. Tests

Update `packages/kilo-jetbrains/frontend/src/test/kotlin/ai/kilocode/client/session/ui/SessionMessageListPanelTest.kt`.

Add imports:

```kotlin
import ai.kilocode.client.session.model.ToolCallRef
import ai.kilocode.client.session.views.ToolView
```

Extend the `part(...)` helper to support tool fields:

```kotlin
private fun part(
    id: String,
    mid: String,
    type: String,
    text: String? = null,
    tool: String? = null,
    callId: String? = null,
    state: String? = null,
) = PartDto(
    id = id,
    sessionID = "ses",
    messageID = mid,
    type = type,
    text = text,
    tool = tool,
    callID = callId,
    state = state,
)
```

Add a question helper overload or parameter:

```kotlin
private fun question(id: String = "q1", tool: ToolCallRef? = null) = Question(
    id = id,
    tool = tool,
    ...
)
```

Add tests:

1. `test active linked question hides matching running question tool`
   - Create panel with prompts.
   - Add assistant message `a1`.
   - Add tool part `tool = "question"`, `callID = "call1"`, `state = "running"`.
   - Assert message `partIds()` initially contains the tool part.
   - Set state to `AwaitingQuestion(question(tool = ToolCallRef("a1", "call1")))`.
   - Assert message `partIds()` is empty and the `QuestionView` is visible.

2. `test clearing active question restores hidden question tool`
   - Same setup.
   - Set awaiting question and assert hidden.
   - Set state to `Idle`.
   - Assert the message `partIds()` contains the tool part again and `part("tool1") is ToolView`.

3. `test active question does not hide unrelated question tool`
   - Add a `question` tool with call id `call1`.
   - Set awaiting question with `ToolCallRef("a1", "call2")` or different message id.
   - Assert the tool part remains visible.

Optional:

4. `test completed question tool remains visible while question active`
   - Add/update matching tool with `state = "completed"` while state is awaiting question.
   - Assert the part is visible because only pending/running placeholders are hidden.

Run targeted tests:

```bash
./gradlew :frontend:test --tests ai.kilocode.client.session.ui.SessionMessageListPanelTest --tests ai.kilocode.client.session.views.QuestionViewTest
```

## Expected Outcome

- The `Question`/`Running` placeholder row from the screenshot disappears for tool-linked active questions.
- The question form still appears in the transcript and keeps the previous layout fixes.
- Standalone question requests without `tool` continue to render as before.
- Completed tool results can appear after resolution, preserving VS Code parity.

## Notes

- Do not change CLI/server DTOs; `PartDto.callID` and `Question.tool` already exist.
- Do not hide all `question` tool rows globally; match by `messageId` and `callId` to avoid hiding unrelated or historical parts.
- Do not suppress completed/error tool results unless a later product decision says JetBrains should hide question tools permanently.
