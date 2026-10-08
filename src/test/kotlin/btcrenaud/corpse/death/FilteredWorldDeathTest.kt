package btcrenaud.corpse.death

import btcrenaud.corpse.world.WorldFilter
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilteredWorldDeathTest {

    @Test
    fun `the vanilla death screen never respawns anyone`() {
        assertFalse(FilteredWorldDeath.VANILLA_DEATH_SCREEN.respawnsInstantly(worldAllowed = false))
        assertFalse(FilteredWorldDeath.VANILLA_DEATH_SCREEN.respawnsInstantly(worldAllowed = true))
    }

    @Test
    fun `instant respawn applies in a filtered world only`() {
        assertTrue(FilteredWorldDeath.INSTANT_RESPAWN.respawnsInstantly(worldAllowed = false))
        assertFalse(FilteredWorldDeath.INSTANT_RESPAWN.respawnsInstantly(worldAllowed = true))
    }

    @Test
    fun `with empty lists the filter has no effect whatever the mode`() {
        val everywhere = WorldFilter.of(emptyList(), emptyList())
        assertFalse(FilteredWorldDeath.INSTANT_RESPAWN.respawnsInstantly(everywhere.allows("world")))
    }

    @Test
    fun `an excluded or unlisted world is filtered`() {
        val filter = WorldFilter.of(listOf("dungeon_*"), listOf("dungeon_lobby"))
        assertFalse(FilteredWorldDeath.INSTANT_RESPAWN.respawnsInstantly(filter.allows("dungeon_procedural_1")))
        assertTrue(FilteredWorldDeath.INSTANT_RESPAWN.respawnsInstantly(filter.allows("dungeon_lobby")))
        assertTrue(FilteredWorldDeath.INSTANT_RESPAWN.respawnsInstantly(filter.allows("world")))
    }
}