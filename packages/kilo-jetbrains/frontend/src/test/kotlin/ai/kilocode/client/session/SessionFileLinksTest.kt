package ai.kilocode.client.session

import ai.kilocode.rpc.dto.WorkspaceFileDto
import junit.framework.TestCase

class SessionFileLinksTest : TestCase() {
    fun `test parse keeps plain path without location`() {
        assertEquals(SessionFileLinks.Target("src/Foo.kt"), SessionFileLinks.parse("src/Foo.kt"))
    }

    fun `test parse strips line range and column suffixes`() {
        assertEquals(SessionFileLinks.Target("src/Foo.kt", line = 12), SessionFileLinks.parse("src/Foo.kt:12"))
        assertEquals(SessionFileLinks.Target("src/Foo.kt", line = 12), SessionFileLinks.parse("src/Foo.kt:12-20"))
        assertEquals(SessionFileLinks.Target("src/Foo.kt", line = 12, column = 3), SessionFileLinks.parse("src/Foo.kt:12:3"))
    }

    fun `test parse preserves encoded path`() {
        assertEquals(SessionFileLinks.Target("src/a%20file.kt", line = 8), SessionFileLinks.parse("src/a%20file.kt:8"))
    }

    fun `test isFileHref separates file refs from urls`() {
        assertTrue(SessionFileLinks.isFileHref("src/Foo.kt"))
        assertTrue(SessionFileLinks.isFileHref("file:///tmp/Foo.kt"))
        assertTrue(SessionFileLinks.isFileHref("C:\\repo\\Foo.kt"))
        assertFalse(SessionFileLinks.isFileHref("https://kilocode.ai/docs"))
        assertFalse(SessionFileLinks.isFileHref("mailto:test@example.com"))
        assertFalse(SessionFileLinks.isFileHref("ftp://example.com/file.txt"))
    }

    fun `test decide returns resolution from open state and candidates`() {
        val one = WorkspaceFileDto("src/Foo.kt", "Foo.kt")
        val two = WorkspaceFileDto("test/Foo.kt", "Foo.kt")

        assertEquals(SessionFileLinks.Resolution.Opened, SessionFileLinks.decide(true, emptyList()))
        assertEquals(SessionFileLinks.Resolution.Missing, SessionFileLinks.decide(false, emptyList()))
        assertEquals(SessionFileLinks.Resolution.OpenDirect(one), SessionFileLinks.decide(false, listOf(one)))
        assertEquals(SessionFileLinks.Resolution.Choose(listOf(one, two)), SessionFileLinks.decide(false, listOf(one, two)))
    }

    fun `test managed worktree storage filter only rejects worktree subtree`() {
        assertFalse(SessionFileLinks.isManagedWorktreeStorage("backend/src/Main.java"))
        assertFalse(SessionFileLinks.isManagedWorktreeStorage(".kilo/plans/x.md"))
        assertTrue(SessionFileLinks.isManagedWorktreeStorage(".kilo/worktrees"))
        assertTrue(SessionFileLinks.isManagedWorktreeStorage(".kilo/worktrees/foo/backend/src/Main.java"))
    }
}
