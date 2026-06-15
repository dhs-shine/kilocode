package ai.kilocode.client.settings.base

import ai.kilocode.client.ui.LayeredOverlayPanel
import ai.kilocode.client.ui.UiStyle
import java.awt.Rectangle

internal open class SettingsOverlayPanel : LayeredOverlayPanel() {
    val progress = SettingsProgressOverlay()

    init {
        addOverlay(progress) { pane, child ->
            val size = child.preferredSize
            Rectangle(
                ((pane.width - size.width) / 2).coerceAtLeast(0),
                UiStyle.Gap.pad(),
                size.width,
                size.height,
            )
        }
    }

    fun showProgress(text: String) {
        progress.showProgress(text)
        syncOverlay()
    }

    fun showProgress(text: String, cancelText: String, cancel: () -> Unit) {
        progress.showProgress(text, cancelText, cancel)
        syncOverlay()
    }

    fun updateProgress(text: String) {
        progress.updateProgress(text)
        syncOverlay()
    }

    fun showError(text: String) {
        progress.showError(text)
        syncOverlay()
    }

    fun clearProgress() {
        progress.clearProgress()
        syncOverlay()
    }

    private fun syncOverlay() {
        overlay.revalidate()
        overlay.repaint()
        content.repaint()
        revalidate()
        repaint()
    }
}
