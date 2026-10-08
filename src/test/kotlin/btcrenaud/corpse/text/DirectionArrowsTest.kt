package btcrenaud.corpse.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class DirectionArrowsTest {

    private fun arrow(dx: Double, dz: Double, yaw: Float) =
        DirectionArrows.DEFAULT[DirectionArrows.indexFor(DirectionArrows.relativeAngle(dx, dz, yaw))]

    @Test
    fun `a corpse straight ahead points up whatever way the player faces`() {
        assertEquals("↑", arrow(0.0, 5.0, 0f))      // facing +Z (south)
        assertEquals("↑", arrow(-5.0, 0.0, 90f))    // facing -X (west)
        assertEquals("↑", arrow(0.0, -5.0, 180f))   // facing -Z (north)
        assertEquals("↑", arrow(5.0, 0.0, 270f))    // facing +X (east)
    }

    @Test
    fun `a corpse behind points down`() {
        assertEquals("↓", arrow(0.0, -5.0, 0f))
    }

    @Test
    fun `facing south a corpse to the west is on the right`() {
        assertEquals("→", arrow(-5.0, 0.0, 0f))
        assertEquals("←", arrow(5.0, 0.0, 0f))
    }

    @Test
    fun `diagonals`() {
        assertEquals("↗", arrow(-5.0, 5.0, 0f))
        assertEquals("↖", arrow(5.0, 5.0, 0f))
    }

    @Test
    fun `negative and large yaws are normalised`() {
        assertEquals(arrow(-5.0, 0.0, 0f), arrow(-5.0, 0.0, 360f))
        assertEquals(arrow(-5.0, 0.0, 90f), arrow(-5.0, 0.0, -270f))
    }

    @Test
    fun `the index stays inside the eight symbols`() {
        var angle = 0.0
        while (angle < 360.0) {
            val index = DirectionArrows.indexFor(angle)
            assert(index in 0 until DirectionArrows.COUNT) { "angle $angle gave $index" }
            angle += 0.5
        }
        assertEquals(0, DirectionArrows.indexFor(359.9))
    }

    @Test
    fun `a list that is not eight long falls back to the defaults`() {
        assertSame(DirectionArrows.DEFAULT, DirectionArrows.usable(listOf("a", "b")))
        assertSame(DirectionArrows.DEFAULT, DirectionArrows.usable(emptyList()))
        val custom = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        assertSame(custom, DirectionArrows.usable(custom))
    }
}