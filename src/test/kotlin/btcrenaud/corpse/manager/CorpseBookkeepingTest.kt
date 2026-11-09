package btcrenaud.corpse.manager

import btcrenaud.corpse.persistence.PurgeMarks
import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CorpseBookkeepingTest {

    @Test
    fun `experience not yet given to the looter goes back to the corpse`() {
        assertEquals(283, CorpseBookkeeping.experienceToPutBack(283, alreadyGivenToLooter = false))
    }

    @Test
    fun `experience already given to the looter is not restored a second time`() {
        assertEquals(0, CorpseBookkeeping.experienceToPutBack(283, alreadyGivenToLooter = true))
    }

    @Test
    fun `a task queued before a shutdown is stale after a reload even though closing was reset`() {
        assertTrue(CorpseBookkeeping.isStale(1, 2, closing = false, pluginEnabled = true))
        assertTrue(CorpseBookkeeping.isStale(1, 1, closing = true, pluginEnabled = true))
        assertTrue(CorpseBookkeeping.isStale(1, 1, closing = false, pluginEnabled = false))
        assertFalse(CorpseBookkeeping.isStale(1, 1, closing = false, pluginEnabled = true))
    }

    @Test
    fun `every unreadable item is reported with its corpse id and raw text`() {
        val id = UUID.randomUUID()
        val lines = CorpseBookkeeping.unreadableReport(id, mapOf(3 to "raw-a", 7 to "raw-b"))
        assertEquals(2, lines.size)
        assertTrue(lines.all { id.toString() in it })
        assertTrue("slot 3" in lines[0] && "raw-a" in lines[0])
        assertTrue("slot 7" in lines[1] && "raw-b" in lines[1])
        assertTrue(CorpseBookkeeping.unreadableReport(id, emptyMap()).isEmpty())
    }

    @Test
    fun `without purge marks the delete failure says no mark was written`() {
        assertTrue("marked for purging" in CorpseBookkeeping.deleteFailureOutcome(marked = true, marksAvailable = true))
        assertTrue("no purge mark written" in CorpseBookkeeping.deleteFailureOutcome(marked = false, marksAvailable = false))
        assertFalse("marked for purging" in CorpseBookkeeping.deleteFailureOutcome(marked = false, marksAvailable = false))
    }

    @Test
    fun `an unreadable purge file yields no marks instead of an exception`() {
        // A directory where the file should be: reading it as text fails.
        val directory = Files.createTempDirectory("corpse-purge-dir").toFile().also { it.deleteOnExit() }
        var reported: Throwable? = null
        assertNull(PurgeMarks.openOrNull(directory) { reported = it })
        assertNotNull(reported)
    }
}
