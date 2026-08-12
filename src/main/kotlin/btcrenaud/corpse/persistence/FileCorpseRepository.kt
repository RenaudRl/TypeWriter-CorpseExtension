package btcrenaud.corpse.persistence

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import btcrenaud.corpse.entity.CorpseAccess
import com.typewritermc.engine.paper.logger
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Corpse storage in a JSON file, used when no MySQL datasource is configured.
 *
 * Items are stored in their encoded form rather than as raw [org.bukkit.inventory.ItemStack]s,
 * which Gson cannot represent.
 */
class FileCorpseRepository(private val file: File) : CorpseRepository {

    private data class StoredCorpse(
        val corpseId: String,
        val ownerUuid: String,
        val ownerName: String,
        val worldUid: String,
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float,
        val pitch: Float,
        val inventory: String,
        val experience: Int,
        val createdAt: Long,
        val expiresAt: Long,
        val onlyOwnerCanLoot: Boolean,
        val ownerProtectionSeconds: Int,
        val definitionId: String,
    )

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val cache = ConcurrentHashMap<UUID, StoredCorpse>()

    override fun initialize(): Boolean = runCatching {
        file.parentFile?.mkdirs()
        if (file.exists()) {
            val type = object : TypeToken<List<StoredCorpse>>() {}.type
            val stored: List<StoredCorpse> = gson.fromJson(file.readText(), type) ?: emptyList()
            stored.forEach { cache[UUID.fromString(it.corpseId)] = it }
        }
        true
    }.getOrElse {
        logger.warning("[Corpse] Could not read ${file.path}: ${it.message}")
        false
    }

    override fun save(record: CorpseRecord) {
        cache[record.corpseId] = StoredCorpse(
            corpseId = record.corpseId.toString(),
            ownerUuid = record.ownerUuid.toString(),
            ownerName = record.ownerName,
            worldUid = record.worldUid.toString(),
            x = record.x,
            y = record.y,
            z = record.z,
            yaw = record.yaw,
            pitch = record.pitch,
            inventory = CorpseItemCodec.encode(record.inventory),
            experience = record.experience,
            createdAt = record.createdAt,
            expiresAt = record.expiresAt,
            onlyOwnerCanLoot = record.access.onlyOwnerCanLoot,
            ownerProtectionSeconds = record.access.ownerProtectionSeconds,
            definitionId = record.definitionId,
        )
        flush()
    }

    override fun delete(corpseId: UUID) {
        if (cache.remove(corpseId) != null) flush()
    }

    override fun loadAll(): List<CorpseRecord> = cache.values.mapNotNull { stored ->
        runCatching {
            CorpseRecord(
                corpseId = UUID.fromString(stored.corpseId),
                ownerUuid = UUID.fromString(stored.ownerUuid),
                ownerName = stored.ownerName,
                worldUid = UUID.fromString(stored.worldUid),
                x = stored.x,
                y = stored.y,
                z = stored.z,
                yaw = stored.yaw,
                pitch = stored.pitch,
                inventory = CorpseItemCodec.decode(stored.inventory),
                experience = stored.experience,
                createdAt = stored.createdAt,
                expiresAt = stored.expiresAt,
                access = CorpseAccess(stored.onlyOwnerCanLoot, stored.ownerProtectionSeconds),
                definitionId = stored.definitionId,
            )
        }.getOrNull()
    }

    private fun flush() {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(gson.toJson(cache.values.toList()))
        }.onFailure { logger.warning("[Corpse] Could not write ${file.path}: ${it.message}") }
    }
}
