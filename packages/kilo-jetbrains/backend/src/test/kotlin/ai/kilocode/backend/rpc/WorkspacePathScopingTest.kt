package ai.kilocode.backend.rpc

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorkspacePathScopingTest {
    private val base = Path.of("/home/user/project")

    @Test
    fun `in-base file returns forward-slash relative path`() {
        assertEquals("src/A.kt", relativeWithinBase(base, Path.of("/home/user/project/src/A.kt")))
    }

    @Test
    fun `nested file returns nested relative path`() {
        assertEquals("a/b/c.kt", relativeWithinBase(base, Path.of("/home/user/project/a/b/c.kt")))
    }

    @Test
    fun `base itself is rejected as blank`() {
        assertNull(relativeWithinBase(base, Path.of("/home/user/project")))
    }

    @Test
    fun `sibling directory outside base is rejected`() {
        assertNull(relativeWithinBase(base, Path.of("/home/user/other/A.kt")))
    }

    @Test
    fun `parent directory is rejected`() {
        assertNull(relativeWithinBase(base, Path.of("/home/user")))
    }

    @Test
    fun `traversal that escapes base is rejected after normalization`() {
        assertNull(relativeWithinBase(base, Path.of("/home/user/project/../secret/A.kt")))
    }

    @Test
    fun `traversal that stays inside base is kept after normalization`() {
        assertEquals("src/A.kt", relativeWithinBase(base, Path.of("/home/user/project/x/../src/A.kt")))
    }

    @Test
    fun `prefix sibling is not treated as inside base`() {
        assertNull(relativeWithinBase(base, Path.of("/home/user/project-2/A.kt")))
    }
}
