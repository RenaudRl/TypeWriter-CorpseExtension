package btcrenaud.corpse.persistence

import btcrenaud.corpse.entity.CorpseAccess
import com.typewritermc.engine.paper.logger
import java.sql.ResultSet
import java.util.UUID
import javax.sql.DataSource

/**
 * Corpse storage on the MySqlExtension datasource.
 *
 * The table is created here rather than by MySqlExtension so the corpse schema stays owned by the
 * extension that uses it, matching how the other BTC extensions manage their own tables.
 */
class MySqlCorpseRepository(private val dataSource: DataSource) : CorpseRepository {

    private companion object {
        const val TABLE = "corpse_corpses"

        const val CREATE = """
            CREATE TABLE IF NOT EXISTS $TABLE (
                corpse_id CHAR(36) NOT NULL PRIMARY KEY,
                owner_uuid CHAR(36) NOT NULL,
                owner_name VARCHAR(32) NOT NULL,
                world_uid CHAR(36) NOT NULL,
                x DOUBLE NOT NULL,
                y DOUBLE NOT NULL,
                z DOUBLE NOT NULL,
                yaw FLOAT NOT NULL,
                pitch FLOAT NOT NULL,
                inventory MEDIUMTEXT NOT NULL,
                experience INT NOT NULL,
                created_at BIGINT NOT NULL,
                expires_at BIGINT NOT NULL,
                only_owner TINYINT(1) NOT NULL,
                protection_seconds INT NOT NULL,
                definition_id VARCHAR(255) NOT NULL,
                INDEX idx_corpse_owner (owner_uuid)
            )
        """

        const val UPSERT = """
            INSERT INTO $TABLE (
                corpse_id, owner_uuid, owner_name, world_uid, x, y, z, yaw, pitch,
                inventory, experience, created_at, expires_at, only_owner, protection_seconds, definition_id
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON DUPLICATE KEY UPDATE
                inventory = VALUES(inventory),
                experience = VALUES(experience),
                expires_at = VALUES(expires_at)
        """
    }

    override fun initialize(): Boolean = runCatching {
        dataSource.connection.use { connection ->
            connection.createStatement().use { it.executeUpdate(CREATE) }
        }
        true
    }.getOrElse {
        logger.warning("[Corpse] Could not create the $TABLE table: ${it.message}")
        false
    }

    override fun save(record: CorpseRecord) {
        runCatching {
            dataSource.connection.use { connection ->
                connection.prepareStatement(UPSERT).use { statement ->
                    statement.setString(1, record.corpseId.toString())
                    statement.setString(2, record.ownerUuid.toString())
                    statement.setString(3, record.ownerName)
                    statement.setString(4, record.worldUid.toString())
                    statement.setDouble(5, record.x)
                    statement.setDouble(6, record.y)
                    statement.setDouble(7, record.z)
                    statement.setFloat(8, record.yaw)
                    statement.setFloat(9, record.pitch)
                    statement.setString(10, CorpseItemCodec.encode(record.inventory))
                    statement.setInt(11, record.experience)
                    statement.setLong(12, record.createdAt)
                    statement.setLong(13, record.expiresAt)
                    statement.setBoolean(14, record.access.onlyOwnerCanLoot)
                    statement.setInt(15, record.access.ownerProtectionSeconds)
                    statement.setString(16, record.definitionId)
                    statement.executeUpdate()
                }
            }
        }.onFailure { logger.warning("[Corpse] Failed to save corpse ${record.corpseId}: ${it.message}") }
    }

    override fun delete(corpseId: UUID) {
        runCatching {
            dataSource.connection.use { connection ->
                connection.prepareStatement("DELETE FROM $TABLE WHERE corpse_id = ?").use { statement ->
                    statement.setString(1, corpseId.toString())
                    statement.executeUpdate()
                }
            }
        }.onFailure { logger.warning("[Corpse] Failed to delete corpse $corpseId: ${it.message}") }
    }

    override fun loadAll(): List<CorpseRecord> = runCatching {
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT * FROM $TABLE").use { statement ->
                statement.executeQuery().use { results ->
                    buildList {
                        while (results.next()) {
                            read(results)?.let { add(it) }
                        }
                    }
                }
            }
        }
    }.getOrElse {
        logger.warning("[Corpse] Failed to load corpses: ${it.message}")
        emptyList()
    }

    private fun read(results: ResultSet): CorpseRecord? = runCatching {
        CorpseRecord(
            corpseId = UUID.fromString(results.getString("corpse_id")),
            ownerUuid = UUID.fromString(results.getString("owner_uuid")),
            ownerName = results.getString("owner_name"),
            worldUid = UUID.fromString(results.getString("world_uid")),
            x = results.getDouble("x"),
            y = results.getDouble("y"),
            z = results.getDouble("z"),
            yaw = results.getFloat("yaw"),
            pitch = results.getFloat("pitch"),
            inventory = CorpseItemCodec.decode(results.getString("inventory")),
            experience = results.getInt("experience"),
            createdAt = results.getLong("created_at"),
            expiresAt = results.getLong("expires_at"),
            access = CorpseAccess(
                onlyOwnerCanLoot = results.getBoolean("only_owner"),
                ownerProtectionSeconds = results.getInt("protection_seconds"),
            ),
            definitionId = results.getString("definition_id"),
        )
    }.getOrNull()
}
