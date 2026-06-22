package ai.kilocode.client.session.ui.prompt

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Path

class PromptMentionPartsTest : BasePlatformTestCase() {

    fun `test mentionFileParts builds file part for tracked relative path`() {
        val parts = mentionFileParts("read @src/Main.kt", setOf("src/Main.kt"), "/repo")

        assertEquals(1, parts.size)
        val part = parts.single()
        assertEquals("file", part.type)
        assertEquals("text/plain", part.mime)
        assertEquals(Path.of("/repo/src/Main.kt").toUri().toString(), part.url)
        assertEquals("Main.kt", part.filename)
        assertEquals("file", part.source?.type)
        assertEquals("src/Main.kt", part.source?.path)
        assertEquals("@src/Main.kt", part.source?.text?.value)
        assertEquals(5.0, part.source?.text?.start)
        assertEquals(17.0, part.source?.text?.end)
    }

    fun `test mentionFileParts ignores untracked path`() {
        assertTrue(mentionFileParts("read @src/Main.kt", setOf("src/Other.kt"), "/repo").isEmpty())
    }

    fun `test mentionFileParts ignores edited mention suffix`() {
        assertTrue(mentionFileParts("read @src/Main.kt-extra", setOf("src/Main.kt"), "/repo").isEmpty())
        assertTrue(mentionFileParts("read @src/Main.kt.bak", setOf("src/Main.kt"), "/repo").isEmpty())
    }

    fun `test mentionFileParts ignores embedded mention text`() {
        assertTrue(mentionFileParts("read foo@src/Main.kt", setOf("src/Main.kt"), "/repo").isEmpty())
    }

    fun `test mentionFileParts keeps absolute paths absolute`() {
        val path = "/tmp/abs.txt"
        val part = mentionFileParts("read @$path", setOf(path), "/repo").single()

        assertEquals(Path.of(path).toUri().toString(), part.url)
        assertEquals("abs.txt", part.filename)
    }

    fun `test gitChangesPart builds encoded data part`() {
        val part = gitChangesPart("review ${MentionAction.GIT_CHANGES.token}", "hello world+plus")!!

        assertEquals("file", part.type)
        assertEquals("text/plain", part.mime)
        assertEquals(MentionAction.GIT_CHANGES.filename, part.filename)
        assertEquals("data:text/plain;charset=utf-8,hello%20world%2Bplus", part.url)
        assertEquals("resource", part.source?.type)
        assertEquals(MentionAction.GIT_CHANGES.uri, part.source?.uri)
        assertEquals("jetbrains", part.source?.clientName)
        assertEquals(MentionAction.GIT_CHANGES.token, part.source?.text?.value)
        assertEquals(7.0, part.source?.text?.start)
        assertEquals(19.0, part.source?.text?.end)
    }

    fun `test gitChangesPart ignores missing blank and non boundary matches`() {
        assertNull(gitChangesPart("review ${MentionAction.GIT_CHANGES.token}", null))
        assertNull(gitChangesPart("review ${MentionAction.GIT_CHANGES.token}", "  "))
        assertNull(gitChangesPart("review ${MentionAction.GIT_CHANGES.token}-foo", "diff"))
        assertNull(gitChangesPart("review foo${MentionAction.GIT_CHANGES.token}", "diff"))
    }
}
