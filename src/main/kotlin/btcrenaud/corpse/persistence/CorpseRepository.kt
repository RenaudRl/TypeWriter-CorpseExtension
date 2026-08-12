package btcrenaud.corpse.persistence

import com.typewritermc.engine.paper.logger
import com.typewritermc.engine.paper.plugin
import java.util.UUID
import javax.sql.DataSource

/**
 * Storage for corpses that must outlive the process.
 *
 * Implementations are called off the server threads by [btcrenaud.corpse.manager.CorpseManager].
 */
interface CorpseRepository {
    /** Create the backing store. Returns false when the store is unusable. */
    fun initialize(): Boolean

    fun save(record: CorpseRecord)

    fun delete(corpseId: UUID)

    fun loadAll(): List<CorpseRecord>

    fun shutdown() {}

    companion object {
        /**
         * MySQL when MySqlExtension has published a [DataSource], otherwise a local file.
         *
         * The file store is not a degraded no-op: losing a player's whole inventory on restart is
         * not an acceptable fallback, so there is always a real store behind this.
         */
        fun create(): CorpseRepository {
            val dataSource = runCatching {
                org.koin.core.context.GlobalContext.get().get<DataSource>()
            }.getOrNull()

            val repository = if (dataSource != null) {
                MySqlCorpseRepository(dataSource)
            } else {
                logger.info("[Corpse] No MySQL DataSource available, storing corpses in the plugin folder.")
                FileCorpseRepository(plugin.dataFolder.resolve("corpse/corpses.json"))
            }

            if (repository.initialize()) return repository

            logger.warning("[Corpse] ${repository::class.simpleName} failed to initialize, falling back to file storage.")
            return FileCorpseRepository(plugin.dataFolder.resolve("corpse/corpses.json"))
                .also { it.initialize() }
        }
    }
}
