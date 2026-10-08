package btcrenaud.corpse.listener

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InteractionRulesTest {

    @Test
    fun `the first packet is always a gesture`() {
        assertTrue(InteractionRules.isNewGesture(null, 1_000, 250))
    }

    @Test
    fun `a packet inside the cooldown is a repeat`() {
        assertFalse(InteractionRules.isNewGesture(1_000, 1_100, 250))
    }

    @Test
    fun `a packet at or after the cooldown is a new gesture`() {
        assertTrue(InteractionRules.isNewGesture(1_000, 1_250, 250))
        assertTrue(InteractionRules.isNewGesture(1_000, 2_000, 250))
    }

    @Test
    fun `a cooldown of zero accepts every packet`() {
        assertTrue(InteractionRules.isNewGesture(1_000, 1_000, 0))
        assertTrue(InteractionRules.isNewGesture(1_000, 1_001, 0))
    }

    @Test
    fun `reach accepts the limit and refuses beyond it`() {
        assertTrue(InteractionRules.isWithinReach(36.0, 6.0))
        assertFalse(InteractionRules.isWithinReach(36.01, 6.0))
    }

    @Test
    fun `another world is never within reach`() {
        assertFalse(InteractionRules.isWithinReach(null, 6.0))
        assertFalse(InteractionRules.isWithinReach(null, 0.0))
    }

    @Test
    fun `a reach of zero or less disables the distance check`() {
        assertTrue(InteractionRules.isWithinReach(10_000.0, 0.0))
        assertTrue(InteractionRules.isWithinReach(10_000.0, -1.0))
    }
}