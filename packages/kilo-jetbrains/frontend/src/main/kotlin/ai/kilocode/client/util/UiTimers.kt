package ai.kilocode.client.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import javax.swing.Timer

interface UiTimer {
    fun start()
    fun stop()
    fun restart()
    fun isRunning(): Boolean
}

interface UiTimerSource {
    fun now(): Long
    fun timer(ms: Int, repeats: Boolean = true, action: () -> Unit): UiTimer
}

object UiTimers : UiTimerSource {
    @Volatile private var source: UiTimerSource = SwingUiTimers()

    override fun now(): Long = source.now()

    override fun timer(ms: Int, repeats: Boolean, action: () -> Unit): UiTimer = source.timer(ms, repeats, action)

    fun replace(source: UiTimerSource, parent: Disposable) {
        val prev = this.source
        this.source = source
        Disposer.register(parent) {
            if (this.source === source) this.source = prev
        }
    }
}

private class SwingUiTimers : UiTimerSource {
    override fun now(): Long = System.currentTimeMillis()

    override fun timer(ms: Int, repeats: Boolean, action: () -> Unit): UiTimer {
        val timer = Timer(ms.coerceAtLeast(0)) { action() }
        timer.isRepeats = repeats
        return SwingUiTimer(timer)
    }

    private class SwingUiTimer(private val timer: Timer) : UiTimer {
        override fun start() {
            timer.start()
        }

        override fun stop() {
            timer.stop()
        }

        override fun restart() {
            timer.restart()
        }

        override fun isRunning(): Boolean = timer.isRunning
    }
}
