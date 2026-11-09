package btcrenaud.corpse.persistence

import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PurgeAndRetryTest {

    private fun file(): File = File(Files.createTempDirectory("corpse-purge").toFile().also { it.deleteOnExit() }, "purge.txt")

    @Test
    fun `a purge mark survives a restart and can be cleared`() {
        val file = file()
        val id = UUID.randomUUID()
        PurgeMarks(file).add(id)

        val reread = PurgeMarks(file)
        assertTrue(reread.contains(id))
        reread.remove(id)
        assertFalse(PurgeMarks(file).contains(id))
    }

    @Test
    fun `garbage lines in the purge file are ignored`() {
        val file = file()
        val id = UUID.randomUUID()
        file.writeText("not an id\n$id\n\n")
        assertEquals(setOf(id), PurgeMarks(file).all())
    }

    @Test
    fun `an operation that fails twice then works succeeds within three attempts`() {
        var calls = 0
        val pauses = mutableListOf<Long>()
        val result = BoundedRetry.run(3, 100, sleep = { pauses += it }) { if (++calls < 3) error("down") }
        assertNull(result)
        assertEquals(3, calls)
        assertEquals(listOf(100L, 200L), pauses)
    }

    @Test
    fun `an operation that never works stops after the allowed attempts and reports the last failure`() {
        var calls = 0
        val failed = mutableListOf<Int>()
        val result = BoundedRetry.run(3, 0, sleep = { }, onFailure = { n, _ -> failed += n }) { calls++; error("down $calls") }
        assertEquals(3, calls)
        assertEquals(listOf(1, 2, 3), failed)
        assertEquals("down 3", result?.message)
    }
}
