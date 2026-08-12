package btcrenaud.corpse.persistence

import btcrenaud.corpse.entity.CorpseAccess
import btcrenaud.corpse.entity.CorpseEntity
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.inventory.ItemStack
import java.util.UUID

/**
 * A corpse in a form that survives a restart.
 *
 * Corpses hold the entire inventory of a dead player. Keeping that only in memory meant a restart,
 * a crash or a reload destroyed it, since the death event already cleared the vanilla drops.
 */
data class CorpseRecord(
    val corpseId: UUID,
    val ownerUuid: UUID,
    val ownerName: String,
    val worldUid: UUID,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
    val inventory: List<ItemStack>,
    val experience: Int,
    val createdAt: Long,
    val expiresAt: Long,
    val access: CorpseAccess,
    val definitionId: String,
) {
    /** The stored location, or null when the world no longer exists on this server. */
    fun toLocation(): Location? {
        val world = Bukkit.getWorld(worldUid) ?: return null
        return Location(world, x, y, z, yaw, pitch)
    }

    companion object {
        fun of(corpse: CorpseEntity): CorpseRecord? {
            val world = corpse.location.world ?: return null
            return CorpseRecord(
                corpseId = corpse.corpseId,
                ownerUuid = corpse.playerUUID,
                ownerName = corpse.playerName,
                worldUid = world.uid,
                x = corpse.location.x,
                y = corpse.location.y,
                z = corpse.location.z,
                yaw = corpse.location.yaw,
                pitch = corpse.location.pitch,
                inventory = corpse.inventory,
                experience = corpse.experience,
                createdAt = corpse.createdAt,
                expiresAt = corpse.expiresAt,
                access = corpse.access,
                definitionId = corpse.definitionId,
            )
        }
    }
}
