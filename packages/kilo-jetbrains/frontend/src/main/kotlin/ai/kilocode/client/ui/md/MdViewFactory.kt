package ai.kilocode.client.ui.md

import ai.kilocode.client.session.ui.SessionStyle

object MdViewFactory {
    fun create(style: SessionStyle = SessionStyle.current()): MdView = hybrid(style)

    fun hybrid(style: SessionStyle = SessionStyle.current()): MdView = MdViewHybrid(style)

    fun html(style: SessionStyle = SessionStyle.current()): MdView = MdViewHtmlPane(style)
}
