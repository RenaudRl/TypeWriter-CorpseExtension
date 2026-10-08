package btcrenaud.corpse.world

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorldFilterTest {

    private val dungeon = "dungeon_procedural_3f2a9c1e-7b44-4c1d-9d0e-5a1b2c3d4e5f"

    @Test
    fun `empty lists mean everywhere`() {
        val filter = WorldFilter.of(emptyList(), emptyList())
        assertTrue(filter.allows("world"))
        assertTrue(filter.allows(dungeon, "minecraft:$dungeon"))
        assertTrue(WorldFilter.EVERYWHERE.allows("anything"))
    }

    @Test
    fun `blank entries are ignored and do not narrow the filter to nothing`() {
        val filter = WorldFilter.of(listOf("", "   "), listOf(" "))
        assertTrue(filter.allows("world"))
    }

    @Test
    fun `a prefix wildcard covers every generated dungeon world`() {
        val filter = WorldFilter.of(listOf("dungeon_*"), emptyList())
        assertTrue(filter.allows(dungeon))
        assertTrue(filter.allows("dungeon_"))
        assertFalse(filter.allows("world"))
        assertFalse(filter.allows("my_dungeon_world"))
    }

    @Test
    fun `an exact name matches that world only`() {
        val filter = WorldFilter.of(listOf("dgbuild"), emptyList())
        assertTrue(filter.allows("dgbuild"))
        assertFalse(filter.allows("dgbuild2"))
        assertFalse(filter.allows("xdgbuild"))
    }

    @Test
    fun `matching ignores case`() {
        val filter = WorldFilter.of(listOf("DgBuild"), emptyList())
        assertTrue(filter.allows("dgbuild"))
        assertTrue(filter.allows("DGBUILD"))
    }

    @Test
    fun `the dimension key is compared as well as the name`() {
        val filter = WorldFilter.of(listOf("minecraft:the_nether"), emptyList())
        assertTrue(filter.allows("world_nether", "minecraft:the_nether"))
        assertFalse(filter.allows("world_nether", "minecraft:overworld"))
        assertFalse(filter.allows("world_nether", null))
    }

    @Test
    fun `an exclusion wins over an allow entry`() {
        val filter = WorldFilter.of(listOf("dungeon_*"), listOf("dungeon_lobby"))
        assertTrue(filter.allows(dungeon))
        assertFalse(filter.allows("dungeon_lobby"))
    }

    @Test
    fun `exclusions alone keep every other world`() {
        val filter = WorldFilter.of(emptyList(), listOf("creative", "plots_*"))
        assertTrue(filter.allows("world"))
        assertFalse(filter.allows("creative"))
        assertFalse(filter.allows("plots_12"))
    }

    @Test
    fun `a question mark stands for exactly one character`() {
        val filter = WorldFilter.of(listOf("arena_?"), emptyList())
        assertTrue(filter.allows("arena_1"))
        assertFalse(filter.allows("arena_"))
        assertFalse(filter.allows("arena_12"))
    }

    @Test
    fun `regex characters in a name are taken literally`() {
        val filter = WorldFilter.of(listOf("world.nether+(1)"), emptyList())
        assertTrue(filter.allows("world.nether+(1)"))
        assertFalse(filter.allows("worldXnether+(1)"))
        assertFalse(filter.allows("world.netherr(1)"))
    }

    @Test
    fun `a lone star allows every world`() {
        assertTrue(WorldFilter.of(listOf("*"), emptyList()).allows("whatever"))
    }

    @Test
    fun `selects treats an empty list as every world`() {
        assertTrue(WorldFilter.selects(emptyList(), "world"))
        assertTrue(WorldFilter.selects(listOf("world_nether"), "world_nether"))
        assertFalse(WorldFilter.selects(listOf("world_nether"), "world"))
    }

    @Test
    fun `globToRegex keeps a wildcard-free pattern anchored`() {
        assertEquals(true, WorldFilter.globToRegex("abc").matches("ABC"))
        assertEquals(false, WorldFilter.globToRegex("abc").matches("abcd"))
    }
}