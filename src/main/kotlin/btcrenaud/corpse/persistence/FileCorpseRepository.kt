package btcrenaud.corpse.persistence

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import btcrenaud.corpse.entity.CorpseAccess
import com.typewritermc.engine.paper.logger
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Corpse storage in a JSON file, used when no MySQL datasource is configured.
 *
 * Items are stored in their encoded form rather than as raw [org.bukkit.inventory.ItemStack]s,
 * which Gson cannot represent.
 *
 * Calls must come from one thread at a time (see [CorpseStorageQueue]); the file is rewritten whole.
 *
 * @param warn receives problems worth an administrator's attention.
 */
class FileCorpseRepository(
    private val file: File,
    private val warn: (String) -> Unit = { logger.warning(it) },
) : CorpseRepository {

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
        if (file.exists()) load()
        true
    }.getOrElse {
        warn("[Corpse] Could not prepare ${file.path}: ${it.message}")
        false
    }

    /**
     * Reads the file into the cache.
     *
     * A file that cannot be parsed is copied aside before anything else happens. The cache would
     * otherwise stay empty and the next save would overwrite the file, destroying every inventory
     * it still held.
     */
    private fun load() {
        val stored: List<StoredCorpse> = try {
            val type = object : TypeToken<List<StoredCorpse>>() {}.type
            gson.fromJson<List<StoredCorpse>?>(file.readText(), type) ?: emptyList()
        } catch (failure: Exception) {
            val backup = keepUnreadableFile()
            warn(
                "[Corpse] ${file.path} could not be read (${failure.message}); " +
                    "starting empty, the unreadable file was kept as ${backup?.path ?: "(copy failed)"}"
            )
            return
        }

        stored.forEach { record ->
            runCatching { cache[UUID.fromString(record.corpseId)] = record }
                .onFailure { warn("[Corpse] Skipped a stored corpse with an invalid id: ${it.message}") }
        }
    }

    private fun keepUnreadableFile(): File? = runCatching {
        val backup = file.resolveSibling("${file.name}.unreadable-${System.currentTimeMillis()}")
        Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING)
        backup
    }.getOrNull()

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
        }.onFailure { warn("[Corpse] Could not decode stored corpse ${stored.corpseId}: ${it.message}") }
            .getOrNull()
    }

    /**
     * Writes the whole cache, through a temporary file renamed over the real one, so a crash in the
     * middle of a write leaves the previous complete file instead of a truncated one.
     */
    private fun flush() {
        runCatching {
            file.parentFile?.mkdirs()
            val temporary = file.resolveSibling("${file.name}.tmp")
            temporary.writeText(gson.toJson(cache.values.toList()))
            replaceFile(temporary)
        }.onFailure { warn("[Corpse] Could not write ${file.path}: ${it.message}") }
    }

    private fun replaceFile(temporary: File) {
        try {
            Files.move(
                temporary.toPath(), file.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
