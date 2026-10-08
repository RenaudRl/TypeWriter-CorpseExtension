package btcrenaud.corpse.death

/**
 * What the server was about to drop for a death, as seen once every other plugin has had its say.
 *
 * @property experience experience orbs the death would have produced.
 */
data class DeathLoot<T : Any>(val items: List<T>, val experience: Int) {

    companion object {
        /**
         * The loot a corpse should take over, or null when the death leaves nothing to take over.
         *
         * The source is the death event's own drop list, not the player's live inventory. The drop
         * list already reflects everything the server and other plugins decided: `keepInventory`
         * leaves it empty, Curse of Vanishing items are not in it, and items a soulbound plugin
         * moved to `getItemsToKeep()` were removed from it. Copying the live inventory instead
         * handed those items to the corpse *and* back to the player on respawn.
         *
         * Returns null for `keepInventory` even when the list is not empty: a plugin that set the
         * flag may have added drops of its own, and the player still keeps their inventory.
         *
         * @param isEmpty whether a drop is an empty stack (air or no amount); empty ones are dropped.
         * @param copy detaches a drop from the event, so the corpse owns its items.
         */
        fun <T : Any> of(
            drops: List<T?>,
            droppedExperience: Int,
            keepInventory: Boolean,
            keepLevel: Boolean,
            isEmpty: (T) -> Boolean,
            copy: (T) -> T,
        ): DeathLoot<T>? {
            if (keepInventory) return null

            val items = drops.filterNotNull().filterNot(isEmpty).map(copy)
            // With keepLevel the player keeps their experience: orbs on top of that would duplicate it.
            val experience = if (keepLevel) 0 else droppedExperience.coerceAtLeast(0)
            return DeathLoot(items, experience)
        }
    }
}
