package ai.kilocode.client.ui.md

import ai.kilocode.client.session.ui.style.SessionEditorStyle

object MdViewFactory {
    fun create(style: SessionEditorStyle = SessionEditorStyle.current()): MdView = hybrid(style)

    fun hybrid(style: SessionEditorStyle = SessionEditorStyle.current()): MdView = MdViewHybrid(style)

    fun html(style: SessionEditorStyle = SessionEditorStyle.current()): MdView = MdViewHtmlPane(style)
}
