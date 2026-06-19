# JetBrains File Mention Rendering Parity

## Goal

Make JetBrains user-message file mentions render like VS Code: show the user's original prompt and file reference, do not show the CLI's synthetic read-tool payload (`<path>`, `<type>`, `<content>`) as visible user text.

## Findings

- VS Code renders user messages through `packages/kilo-vscode/webview-ui/src/components/chat/VscodeUserMessage.tsx` and `packages/kilo-ui/src/components/message-part.tsx`.
- VS Code chooses the first non-synthetic text part for the visible prompt: `part.type === "text" && !part.synthetic`.
- VS Code keeps file parts available for user-message rendering, but text-file mentions are not shown as raw read output.
- The CLI expands text file parts in `packages/opencode/src/session/prompt.ts` into synthetic text parts containing read-tool input/output plus the original file part.
- JetBrains currently drops the `synthetic` part field in `PartDto` / `KiloCliDataParser.parsePart`, so synthetic read output is converted into normal `Text` and rendered by `MessageView` / `PromptView`.
- JetBrains already has `PromptAttachmentView` for user `FileAttachment` parts, but it cannot distinguish mention-derived text files from explicit text-file attachments because `PromptPartDto` does not carry `source` metadata.

## Implementation Plan

1. Preserve CLI part metadata in JetBrains DTOs.
   - Add `synthetic: Boolean? = null` to `PartDto` in `packages/kilo-jetbrains/shared/src/main/kotlin/ai/kilocode/rpc/dto/ChatDto.kt`.
   - Add small serializable source DTOs for file-part source metadata, e.g. `PartSourceDto` and `PartSourceTextDto`.
   - Add `source: PartSourceDto? = null` to both `PartDto` and `PromptPartDto`.

2. Parse and serialize the new fields in the backend bridge.
   - Update `KiloCliDataParser.parsePart(...)` to read top-level `synthetic` and `source` from CLI message parts.
   - Update `KiloCliDataParser.buildPromptPartJson(...)` to include `source` for outgoing file prompt parts.
   - Keep the existing `sanitizeUserPromptText(...)` fallback for older or malformed events that do not include `synthetic`.
   - Add parser tests for `synthetic`, source parsing, and prompt JSON source serialization.

3. Send source metadata for JetBrains mentions.
   - Update `SessionUi.mentionParts(...)` so each `@path` file mention includes `source = { type: "file", path, text: { value: "@path", start, end } }`.
   - Update `dataPart(...)` call sites for `@terminal` and `@git-changes` to include equivalent source text metadata when feasible.
   - Use the exact mention token offsets from the submitted prompt so downstream rendering can distinguish mention-backed context from manually attached files.

4. Hide synthetic user text in the JetBrains session model/rendering path.
   - Add `synthetic` to the `Text` model or otherwise track hidden synthetic part IDs in `SessionModel`.
   - In `SessionModel.updateContent(...)` and `loadHistory(...)`, skip user `text` parts where `dto.synthetic == true`.
   - Ensure a later update that marks an existing user text part synthetic removes the visible content.
   - Ensure deltas for already-hidden synthetic part IDs do not create a visible prompt part.
   - Do not hide assistant synthetic text unless there is an explicit existing behavior to match.

5. Match VS Code's text-file mention display more closely.
   - Extend `FileAttachment` with optional source metadata.
   - In `MessageView`, do not add source-backed `text/plain` user file parts to `PromptAttachmentView`; keep source-less text files as cards so explicit attachments still render.
   - Keep image/PDF user attachments as attachment cards, matching VS Code's separate attachment preview behavior.
   - Optional polish if scope allows: make source-backed `@file` tokens in `PromptView` clickable via `openFile(source.path)` using existing markdown/link styling. Do this only if it stays small and does not require rebuilding the retained Swing view architecture.

6. Add focused tests.
   - `KiloCliDataParserTest`: parse `synthetic: true`; parse file `source`; build prompt JSON with source; history preserves source and synthetic flags.
   - `SessionModelTest`: user synthetic text is skipped in history and live updates; assistant text is unchanged; synthetic deltas do not create visible text after a synthetic update.
   - `SessionUiUpdateTest`: a user message containing original text, a synthetic read-output text part, and a source-backed text/plain file shows only the prompt text and no raw `<path>/<content>` text; source-less text/plain attachments still render as `PromptAttachmentView`.

7. Release note and verification.
   - Add a patch changeset for the JetBrains plugin: "Hide raw file contents from mentioned files in JetBrains chat messages."
   - Run the smallest relevant checks from `packages/kilo-jetbrains/`: targeted Gradle tests for parser/model/UI update tests, then `./gradlew typecheck` if the targeted tests pass.

## Non-Goals

- Do not change `packages/opencode/src/session/prompt.ts`; the CLI already marks these parts as synthetic.
- Do not add brittle string stripping for the full `<path>/<type>/<content>` block as the main fix. Metadata-based filtering matches VS Code and avoids hiding user-authored text.
- Do not remove attachment cards for manually attached text files that are not source-backed mentions.
