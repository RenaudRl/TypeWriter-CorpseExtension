package btcrenaud.corpse.manager

import btcrenaud.corpse.persistence.PurgeMarks
import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun `only the corpses of the named world are selected`() {
        val corpses = listOf("a" to "dungeon_classic_x", "b" to "world", "c" to null, "d" to "dungeon_classic_x")
        assertEquals(listOf("a", "d"), CorpseBookkeeping.inWorld(corpses, "dungeon_classic_x") { it.second }.map { it.first })
        assertTrue(CorpseBookkeeping.inWorld(corpses, "nether") { it.second }.isEmpty())
    }

    @Test
    fun `what does not fit in the inventory becomes surplus, by slot`() {
        var room = 2
        val items = linkedMapOf(0 to "sword", 5 to "shield", 9 to "apple", 12 to "bow")
        val pending = LinkedHashMap(items)
        val surplus = linkedMapOf<Int, String>()

        CorpseBookkeeping.handOver(items, pending, surplus) { item -> if (room-- > 0) null else item }

        assertEquals(mapOf(9 to "apple", 12 to "bow"), surplus)
        assertTrue(pending.isEmpty(), "every item was offered")
    }

    @Test
    fun `a hand-over that fails half way leaves the items not offered pending and none in both maps`() {
        val items = linkedMapOf(0 to "a", 1 to "b", 2 to "c")
        val pending = LinkedHashMap(items)
        val surplus = linkedMapOf<Int, String>()

        assertFailsWith<IllegalStateException> {
            CorpseBookkeeping.handOver(items, pending, surplus) { item -> if (item == "c") error("boom") else if (item == "b") item else null }
        }

        assertEquals(mapOf(1 to "b"), surplus)
        assertEquals(mapOf(2 to "c"), pending)
    }

    @Test
    fun `the dormant corpse report names the corpse, its owner, the item count and the world`() {
        val id = UUID.randomUUID()
        val world = UUID.randomUUID()
        val line = CorpseBookkeeping.dormantReport(id, "Alex", 7, world)
        assertTrue(listOf(id.toString(), "Alex", "7 item(s)", world.toString()).all { it in line })
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
