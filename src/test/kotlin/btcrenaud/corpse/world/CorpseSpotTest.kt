package btcrenaud.corpse.world

import kotlin.test.Test
import kotlin.test.assertEquals

class CorpseSpotTest {

    private val minY = -64
    private val maxY = 320

    @Test
    fun `a death inside the world keeps its height`() {
        assertEquals(70.5, CorpseSpot.resolveY(70.5, minY, maxY) { error("not needed") })
        assertEquals(-64.0, CorpseSpot.resolveY(-64.0, minY, maxY) { error("not needed") })
    }

    @Test
    fun `a death in the void is lifted to the lowest standable block`() {
        val ground = 62
        val result = CorpseSpot.resolveY(-200.0, minY, maxY) { it >= ground }
        assertEquals(62.0, result)
    }

    @Test
    fun `a death above the build limit comes down to the highest standable block`() {
        val result = CorpseSpot.resolveY(400.0, minY, maxY) { it in 10..80 }
        assertEquals(80.0, result)
    }

    @Test
    fun `with no ground at all the corpse stays at the point of death bounded to the world`() {
        assertEquals(-64.0, CorpseSpot.resolveY(-500.0, minY, maxY) { false })
        assertEquals(319.0, CorpseSpot.resolveY(900.0, minY, maxY) { false })
    }
}
