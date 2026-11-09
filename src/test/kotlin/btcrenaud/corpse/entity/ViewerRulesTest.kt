package btcrenaud.corpse.entity

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ViewerRulesTest {

    private val world = UUID.randomUUID()
    private val spot = PlayerSpot(world, 10.0, 64.0, 0.0)

    @Test
    fun `a player inside the range in the same world sees the corpse`() {
        assertTrue(ViewerRules.isWithin(spot, world, 0.0, 64.0, 0.0, 10.0 * 10.0))
    }

    @Test
    fun `a player beyond the range does not`() {
        assertFalse(ViewerRules.isWithin(spot, world, 0.0, 64.0, 0.0, 9.9 * 9.9))
    }

    @Test
    fun `another world never counts however close`() {
        assertFalse(ViewerRules.isWithin(spot, UUID.randomUUID(), 10.0, 64.0, 0.0, 1_000_000.0))
    }

    @Test
    fun `a player nobody has a position for is not a viewer`() {
        assertFalse(ViewerRules.isWithin(null, world, 0.0, 0.0, 0.0, 1_000_000.0))
    }

    @Test
    fun `a failing viewer is left alone for longer each time up to a cap`() {
        val backoff = SpawnBackoff(firstDelayMillis = 1_000, maxDelayMillis = 4_000)
        val player = UUID.randomUUID()
        assertTrue(backoff.allowed(player, 0))

        assertEquals(1, backoff.failed(player, 0))
        assertFalse(backoff.allowed(player, 999))
        assertTrue(backoff.allowed(player, 1_000))

        assertEquals(2, backoff.failed(player, 1_000))
        assertFalse(backoff.allowed(player, 2_999))
        assertTrue(backoff.allowed(player, 3_000))

        backoff.failed(player, 3_000)
        assertEquals(4, backoff.failed(player, 7_000))
        assertFalse(backoff.allowed(player, 10_999), "capped at the maximum delay")
        assertTrue(backoff.allowed(player, 11_000))
    }

    @Test
    fun `a viewer who left starts again from the first delay`() {
        val backoff = SpawnBackoff(1_000, 4_000)
        val player = UUID.randomUUID()
        backoff.failed(player, 0)
        backoff.forget(player)
        assertTrue(backoff.allowed(player, 0))
        assertEquals(1, backoff.failed(player, 0))
    }
}
