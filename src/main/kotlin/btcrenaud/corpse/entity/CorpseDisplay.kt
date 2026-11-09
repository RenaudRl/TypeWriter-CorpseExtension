package btcrenaud.corpse.entity

import com.typewritermc.engine.paper.entry.entity.EntityCreator
import com.typewritermc.engine.paper.entry.entity.FakeEntity
import com.typewritermc.engine.paper.entry.entity.PositionProperty
import com.typewritermc.engine.paper.entry.entity.entityShowRange
import com.typewritermc.engine.paper.entry.entries.EntityProperty
import com.typewritermc.engine.paper.logger
import btcrenaud.corpse.utils.CorpseDiagnostics
import btcrenaud.corpse.utils.PlayerPositions
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Renders one corpse to every player that can see it.
 *
 * A [FakeEntity] is bound to a single viewer by construction — [EntityCreator.create] takes the
 * player it sends packets to, and [FakeEntity.spawn] registers that player as the only viewer. So
 * one corpse needs one entity *per viewer*, which is what the engine's own displays do
 * (`SharedAudienceEntityDisplay` + `DisplayEntity`). This mirrors that structure, minus the
 * activity/pathfinding machinery a corpse has no use for: a corpse never moves.
 *
 * Corpses are created at runtime from a death, not authored on a page, so they cannot go through
 * `EntityInstanceEntry`/`AudienceFilter`. Viewer tracking is therefore done here against the
 * engine's configured show range.
 */
class CorpseDisplay(
    private val creator: EntityCreator,
    val position: PositionProperty,
    /** Unique id of the Bukkit world [position] is in; [position] itself only carries its name. */
    private val worldId: UUID,
    initialProperties: List<EntityProperty> = emptyList(),
    private val showRange: Double = entityShowRange,
    /**
     * Run once per viewer, right after their copy of the corpse is spawned.
     *
     * Model animations are triggered per entity, not carried by a property, so a player walking
     * into range later has to be given the animation too — otherwise their corpse stands idle
     * while everyone else sees it lying down.
     */
    private val onSpawned: (FakeEntity) -> Unit = {},
) {
    /**
     * One viewer's copy of the corpse.
     *
     * [root] is what the viewer is shown and what interaction packets resolve against; [model] is
     * the entity actually carrying the skin or the 3D model. They differ whenever the corpse is
     * wrapped in an interaction hit box, and the wrapper does not forward `tick()` — so the model is
     * kept alongside rather than looked up per tick.
     */
    private class View(val root: FakeEntity) {
        val model: FakeEntity = root.corpseModel()
    }

    private val entities = ConcurrentHashMap<UUID, View>()
    private val spawnBackoff = SpawnBackoff()

    @Volatile
    private var properties: List<EntityProperty> = initialProperties

    @Volatile
    private var disposed = false

    /** Viewers the corpse is currently spawned in for. */
    val viewers: Set<UUID> get() = entities.keys.toSet()

    /**
     * Refresh the viewer set and advance every spawned entity.
     *
     * [FakeEntity.tick] is what drives model animation and nameplates on the BetterModel, BTCMobs
     * and MythicMobs backends — without it those models render frozen.
     */
    fun tick() {
        if (disposed) return

        val rangeSquared = showRange * showRange
        val now = System.currentTimeMillis()

        // Positions come from [PlayerPositions], sampled on each player's own thread: this runs on
        // the global region, which may not read a player's location on Folia.
        // Drop viewers that went offline, changed world or walked out of range.
        entities.entries.removeIf { (playerId, view) ->
            val stillSees = inRange(PlayerPositions.spot(playerId), rangeSquared)
            if (!stillSees) runCatching { view.root.dispose() }
            !stillSees
        }

        // Spawn for viewers that came into range.
        PlayerPositions.forEach { playerId, spot ->
            if (entities.containsKey(playerId)) return@forEach
            if (!inRange(spot, rangeSquared)) {
                spawnBackoff.forget(playerId)
                return@forEach
            }
            if (!spawnBackoff.allowed(playerId, now)) return@forEach
            val player = Bukkit.getPlayer(playerId) ?: return@forEach
            val entity = spawnFor(player, now)
            if (entity != null) {
                entities[playerId] = View(entity)
                spawnBackoff.forget(playerId)
            }
        }

        entities.values.forEach { view ->
            runCatching { view.root.tick() }
            // The hit box wrapper's tick is the engine's empty default, so the model needs its own.
            if (view.model !== view.root) runCatching { view.model.tick() }
        }
    }

    /**
     * Replace the properties pushed to every viewer. Used for state that changes over the corpse's
     * life — glow, display name, equipment.
     */
    fun updateProperties(properties: List<EntityProperty>) {
        this.properties = properties
        if (disposed || properties.isEmpty()) return
        entities.values.forEach { view -> runCatching { view.root.consumeProperties(properties) } }
    }

    /** Whether [entityId] belongs to this corpse, as seen by [playerId]. */
    fun isSeenBy(playerId: UUID, entityId: Int): Boolean = entities[playerId]?.root?.contains(entityId) == true

    /** The entity id this corpse currently uses for [playerId], or null if not spawned in. */
    fun entityIdFor(playerId: UUID): Int? = entities[playerId]?.root?.entityId

    fun dispose() {
        disposed = true
        entities.values.forEach { view -> runCatching { view.root.dispose() } }
        entities.clear()
    }

    private fun inRange(spot: PlayerSpot?, rangeSquared: Double): Boolean =
        ViewerRules.isWithin(spot, worldId, position.x, position.y, position.z, rangeSquared)

    private fun spawnFor(player: Player, now: Long): FakeEntity? = runCatching {
        val entity = creator.create(player)
        // Order matters: spawn() sends the spawn packet and registers the viewer, properties are
        // only meaningful on an entity the client already knows about.
        entity.spawn(position)
        properties.takeIf { it.isNotEmpty() }?.let { entity.consumeProperties(it) }
        onSpawned(entity)
        CorpseDiagnostics.spawned(player, entity)
        entity
    }.onFailure {
        // Swallowing this is what hid the real cause of "the corpse does not react" for two rounds:
        // a throw here leaves no entity and no message at all. The whole trace is given once per
        // run of failures; the backoff keeps the retries from repeating it every tick.
        val failures = spawnBackoff.failed(player.uniqueId, now)
        val detail = if (failures == 1) it.stackTraceToString() else "${it::class.simpleName}: ${it.message}"
        logger.warning("[Corpse] Failed to spawn corpse for ${player.name} (failure $failures in a row, retrying later): $detail")
    }.getOrNull()
}
