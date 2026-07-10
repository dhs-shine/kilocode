package ai.kilocode.backend.cli

import ai.kilocode.backend.testing.TestLog
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KiloBackendCliKillTest {

    @Test
    fun `kills a real process non-windows path`() {
        val log = TestLog()
        val proc = process("sleep", "30")
        try {
            killCliProcessTree(proc, log, windows = false)
            assertTrue(proc.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS), "process did not exit")
            assertFalse(proc.isAlive)
        } finally {
            cleanup(proc)
        }
    }

    @Test
    fun `kills a real process tree`() {
        val log = TestLog()
        // The parent backgrounds two children and only `wait`s — it installs no TERM trap
        // and does not forward signals. Destroying the parent alone would orphan the
        // children, so passing assertions prove killCliProcessTree killed the whole tree.
        val proc = process("sh", "-c", "sleep 30 & sleep 30 & wait")
        // Wait for both children so the captured set matches what the kill enumerates; otherwise a
        // late-forked sleep could be asserted on but never seen by killCliProcessTree.
        val kids = descendants(proc, min = 2)
        try {
            assertTrue(kids.size >= 2, "process tree did not spawn both descendants (found ${kids.size})")

            killCliProcessTree(proc, log, windows = false)

            assertTrue(proc.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS), "parent process did not exit")
            assertFalse(proc.isAlive)
            kids.forEach { child -> assertTrue(exited(child), "child process ${child.pid()} is still alive") }
        } finally {
            kids.forEach { it.destroyForcibly() }
            cleanup(proc)
        }
    }

    @Test
    fun `no-wait path escalates to SIGKILL for a SIGTERM-ignoring process`() {
        val log = TestLog()
        // The parent ignores SIGTERM, so only SIGKILL can stop it. This is the shutdown-hook path
        // (wait=false); it must still escalate rather than orphan a tree that survives SIGTERM.
        val proc = process("sh", "-c", "trap '' TERM; sleep 30")
        try {
            killCliProcessTree(proc, log, wait = false, windows = false)
            assertTrue(proc.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS), "process did not exit after SIGKILL")
            assertFalse(proc.isAlive)
        } finally {
            cleanup(proc)
        }
    }

    @Test
    fun `windows path fallback terminates process on this OS`() {
        val log = TestLog()
        val proc = process("sleep", "30")
        try {
            killCliProcessTree(proc, log, windows = true)
            assertTrue(proc.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS), "process did not exit")
            assertFalse(proc.isAlive)
        } finally {
            cleanup(proc)
        }
    }

    @Test
    fun `double kill is a no-op`() {
        val log = TestLog()
        val proc = process("sleep", "30")
        try {
            killCliProcessTree(proc, log, windows = false)
            assertTrue(proc.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS), "process did not exit")
            killCliProcessTree(proc, log, windows = false)
            assertFalse(proc.isAlive)
        } finally {
            cleanup(proc)
        }
    }

    private fun process(vararg cmd: String): Process = ProcessBuilder(*cmd).start()

    private fun descendants(proc: Process, min: Int = 1): List<ProcessHandle> {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(PROCESS_TIMEOUT_SECONDS)
        while (System.nanoTime() < end) {
            val kids = proc.toHandle().descendants().toList()
            if (kids.size >= min) return kids
            Thread.sleep(25)
        }
        return proc.toHandle().descendants().toList()
    }

    private fun exited(child: ProcessHandle): Boolean {
        if (!child.isAlive) return true
        return runCatching {
            child.onExit().get(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            true
        }.getOrDefault(!child.isAlive)
    }

    private fun cleanup(proc: Process) {
        proc.toHandle().descendants().forEach { it.destroyForcibly() }
        proc.destroyForcibly()
        proc.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    companion object {
        private const val PROCESS_TIMEOUT_SECONDS = 5L
    }
}
