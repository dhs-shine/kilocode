package ai.kilocode.client.session.views

import ai.kilocode.client.session.model.Text
import ai.kilocode.client.session.ui.selection.SessionSelection
import ai.kilocode.client.session.ui.style.SessionEditorStyle

class PromptView(
    text: Text,
    openUrl: (String) -> Unit = {},
    selection: SessionSelection? = null,
) : TextView(text, transparent = true, openUrl = openUrl, selection = selection) {

    override fun styleFont(style: SessionEditorStyle) = style.editorFont

    override fun styleBackground(style: SessionEditorStyle) = style.editorBackground

    override fun dumpLabel() = "PromptView#$contentId"
}
