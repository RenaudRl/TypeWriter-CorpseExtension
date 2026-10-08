package btcrenaud.corpse.entity

import com.typewritermc.engine.paper.entry.entries.EntityProperty
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID

/**
 * Corpse backed by a [CorpseDisplay].
 *
 * The inventory is mutable so items can be taken one at a time from the loot GUI; everything else
 * about the corpse is fixed for its lifetime.
 */
class SimpleCorpseEntity(
    override val corpseId: UUID = UUID.randomUUID(),
    override val playerUUID: UUID,
    override val playerName: String,
    inventory: List<ItemStack>,
    override val experience: Int,
    override val location: Location,
    override val access: CorpseAccess = CorpseAccess.OPEN,
    override val definitionId: String = "",
    override val createdAt: Long = System.currentTimeMillis(),
    override val expiresAt: Long = 0L,
    private val display: CorpseDisplay,
) : CorpseEntity {

    private val mutableInventory = inventory.map { it.clone() }.toMutableList()

    // Guarded by the list itself: clicks on a corpse can come from several regions at once.
    override val inventory: List<ItemStack>
        get() = synchronized(mutableInventory) { mutableInventory.map { it.clone() } }

    override fun tick() = display.tick()

    override fun dispose() = display.dispose()

    override fun isSeenBy(playerId: UUID, entityId: Int): Boolean = display.isSeenBy(playerId, entityId)

    override fun entityIdFor(playerId: UUID): Int? = display.entityIdFor(playerId)

    override fun canLoot(player: Player): Boolean {
        if (player.uniqueId == playerUUID) return true
        if (access.onlyOwnerCanLoot) return false
        if (access.ownerProtectionSeconds <= 0) return true
        val elapsed = System.currentTimeMillis() - createdAt
        return elapsed >= access.ownerProtectionSeconds * 1000L
    }

    override fun removeItem(slot: Int): ItemStack? = synchronized(mutableInventory) {
        if (slot !in mutableInventory.indices) return@synchronized null
        val item = mutableInventory[slot]
        if (item.type.isAir) return@synchronized null
        mutableInventory[slot] = ItemStack(Material.AIR)
        item
    }

    override fun isEmpty(): Boolean = synchronized(mutableInventory) { mutableInventory.all { it.type.isAir } }

    /** Push updated render state (glow, name, equipment) to every viewer. */
    fun updateProperties(properties: List<EntityProperty>) = display.updateProperties(properties)
}
