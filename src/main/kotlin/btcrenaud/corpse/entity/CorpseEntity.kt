package btcrenaud.corpse.entity

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID

/**
 * Who is allowed to loot a corpse, captured when the corpse is created.
 *
 * Snapshotting the rule rather than reading it live means a settings change mid-life cannot
 * retroactively unlock corpses that died under stricter rules.
 */
data class CorpseAccess(
    val onlyOwnerCanLoot: Boolean = false,
    /** Seconds during which only the owner may loot, after which the corpse opens to everyone. */
    val ownerProtectionSeconds: Int = 0,
) {
    companion object {
        val OPEN = CorpseAccess()
    }
}

/**
 * A player corpse in the world.
 *
 * Rendering is delegated to a [CorpseDisplay], which keeps one fake entity per viewer. The corpse
 * itself owns the loot, the lifetime and the access rule.
 */
interface CorpseEntity {
    val corpseId: UUID
    val playerUUID: UUID
    val playerName: String
    val inventory: List<ItemStack>
    val experience: Int
    val location: Location
    val createdAt: Long
    val access: CorpseAccess

    /** Id of the [btcrenaud.corpse.entries.CorpseDefinitionEntry] this corpse renders with. */
    val definitionId: String

    /**
     * Epoch millis at which this corpse expires, or 0 for never.
     *
     * Resolved once from the corpse's own definition when it is created. Recomputing it per tick
     * from the first definition found in the project made every corpse share one lifetime.
     */
    val expiresAt: Long

    /** Advance rendering: refresh viewers and tick every spawned entity. */
    fun tick()

    /** Remove the corpse from every viewer. */
    fun dispose()

    /** Whether [entityId], as seen by [playerId], is this corpse. */
    fun isSeenBy(playerId: UUID, entityId: Int): Boolean

    /** The entity id this corpse currently uses for [playerId], or null if not spawned in for them. */
    fun entityIdFor(playerId: UUID): Int?

    /** Whether [player] may take loot from this corpse right now. */
    fun canLoot(player: Player): Boolean

    /** Remove the item at [slot] and return it, or null if empty or out of bounds. */
    fun removeItem(slot: Int): ItemStack?

    /** Whether every slot is now empty. */
    fun isEmpty(): Boolean
}
