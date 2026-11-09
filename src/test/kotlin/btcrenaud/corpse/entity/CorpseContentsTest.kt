package btcrenaud.corpse.entity

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CorpseContentsTest {

    private fun contents(items: List<String>, experience: Int = 0) =
        CorpseContents(items, experience, isEmpty = { it.isEmpty() }, empty = { "" }, copy = { it })

    @Test
    fun `take all empties the corpse and keeps slot indexes`() {
        val corpse = contents(listOf("a", "", "b"), 9)
        assertEquals(mapOf(0 to "a", 2 to "b"), corpse.takeAll())
        assertTrue(corpse.isEmpty())
        assertTrue(corpse.takeAll().isEmpty())
        assertEquals(listOf("", "", ""), corpse.items)
    }

    @Test
    fun `an item taken from a slot is not in take all`() {
        val corpse = contents(listOf("a", "b"))
        assertEquals("a", corpse.remove(0))
        assertEquals(mapOf(1 to "b"), corpse.takeAll())
        assertNull(corpse.remove(1))
    }

    @Test
    fun `experience is taken once and can be put back`() {
        val corpse = contents(listOf("a"), 12)
        assertEquals(12, corpse.takeExperience())
        assertEquals(0, corpse.takeExperience())
        corpse.restoreExperience(12)
        assertEquals(12, corpse.heldExperience)
    }

    @Test
    fun `restore refuses an occupied or unknown slot`() {
        val corpse = contents(listOf("a", ""))
        assertFalse(corpse.restore(0, "x"))
        assertFalse(corpse.restore(5, "x"))
        assertTrue(corpse.restore(1, "x"))
        assertEquals(listOf("a", "x"), corpse.items)
    }

    @Test
    fun `take all racing with slot clicks gives every item to exactly one taker`() {
        val pool = Executors.newFixedThreadPool(9)
        try {
            repeat(200) {
                val size = 36
                val corpse = contents((0 until size).map { "item$it" })
                val taken = Collections.synchronizedList(mutableListOf<String>())
                val start = CountDownLatch(1)
                val done = CountDownLatch(9)

                pool.execute { start.await(); taken += corpse.takeAll().values; done.countDown() }
                repeat(8) { worker ->
                    pool.execute {
                        start.await()
                        for (slot in worker until size step 8) corpse.remove(slot)?.let { taken += it }
                        done.countDown()
                    }
                }
                start.countDown()
                assertTrue(done.await(5, TimeUnit.SECONDS))

                assertEquals(size, taken.size, "an item was lost or handed out twice")
                assertEquals(size, taken.toSet().size)
            }
        } finally {
            pool.shutdownNow()
        }
    }
}
