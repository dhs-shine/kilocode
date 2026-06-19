package ai.kilocode.backend.rpc

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorkspacePathScopingTest {
    // Derive an absolute, OS-portable base from the real home dir so the test runs identically on
    // Windows, macOS, and Linux (no hardcoded POSIX "/home/..." literals).
    private val base: Path = Path.of(System.getProperty("user.home")).resolve("kilo-scope-test").normalize()

    private fun at(vararg segments: String): Path = segments.fold(base) { acc, s -> acc.resolve(s) }

    @Test
    fun `in-base file returns forward-slash relative path`() {
        assertEquals("src/A.kt", relativeWithinBase(base, at("src", "A.kt")))
    }

    @Test
    fun `nested file returns nested relative path`() {
        assertEquals("a/b/c.kt", relativeWithinBase(base, at("a", "b", "c.kt")))
    }

    @Test
    fun `base itself is rejected as blank`() {
        assertNull(relativeWithinBase(base, base))
    }

    @Test
    fun `sibling directory outside base is rejected`() {
        assertNull(relativeWithinBase(base, base.resolveSibling("other").resolve("A.kt")))
    }

    @Test
    fun `parent directory is rejected`() {
        assertNull(relativeWithinBase(base, base.parent))
    }

    @Test
    fun `traversal that escapes base is rejected after normalization`() {
        assertNull(relativeWithinBase(base, base.resolve("..").resolve("secret").resolve("A.kt")))
    }

    @Test
    fun `traversal that stays inside base is kept after normalization`() {
        assertEquals("src/A.kt", relativeWithinBase(base, base.resolve("x").resolve("..").resolve("src").resolve("A.kt")))
    }

    @Test
    fun `prefix sibling is not treated as inside base`() {
        val sibling = base.resolveSibling(base.fileName.toString() + "-2")
        assertNull(relativeWithinBase(base, sibling.resolve("A.kt")))
    }
}
