package btcrenaud.corpse.death

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull

class DeathLootTest {

    private fun loot(
        drops: List<String?>,
        experience: Int = 0,
        keepInventory: Boolean = false,
        keepLevel: Boolean = false,
    ) = DeathLoot.of(drops, experience, keepInventory, keepLevel, isEmpty = { it.isBlank() }, copy = { String(it.toCharArray()) })

    @Test
    fun `keepInventory leaves the death to vanilla even when drops were added`() {
        assertNull(loot(listOf("stick"), experience = 10, keepInventory = true))
    }

    @Test
    fun `the drop list is the loot, empty stacks and nulls are dropped`() {
        val result = loot(listOf("sword", null, "", "apple"), experience = 7)!!
        assertEquals(listOf("sword", "apple"), result.items)
        assertEquals(7, result.experience)
    }

    @Test
    fun `keepLevel means no orbs on top of the kept experience`() {
        assertEquals(0, loot(listOf("sword"), experience = 40, keepLevel = true)!!.experience)
    }

    @Test
    fun `a negative experience is clamped to zero`() {
        assertEquals(0, loot(listOf("sword"), experience = -5)!!.experience)
    }

    @Test
    fun `an empty death gives an empty loot rather than null`() {
        val result = loot(emptyList())!!
        assertEquals(emptyList(), result.items)
    }

    @Test
    fun `items are copied so the corpse owns them`() {
        val source = StringBuilder("x").toString()
        val result = loot(listOf(source))!!
        assertNotSame(source, result.items[0])
    }
}