package btcrenaud.corpse.death

/** What a death looks like in a world where the world filter says no corpse. */
enum class FilteredWorldDeath {
    /** The ordinary Minecraft death: the player lies on the ground and sees the death screen. */
    VANILLA_DEATH_SCREEN,

    /** The player is respawned on the next tick, without the death screen. */
    INSTANT_RESPAWN;

    /**
     * Whether a player who just died must be respawned for them.
     *
     * Only a world the filter turned down is concerned: where a corpse is left (or the filter lets
     * every world through, which is what empty lists mean) the death is left alone, whatever the mode.
     *
     * @param worldAllowed whether the world filter lets this world have corpses.
     */
    fun respawnsInstantly(worldAllowed: Boolean): Boolean = this == INSTANT_RESPAWN && !worldAllowed
}