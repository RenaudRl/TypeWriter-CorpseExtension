package btcrenaud.corpse.entity

import com.typewritermc.engine.paper.entry.entity.EntityCreator
import com.typewritermc.engine.paper.entry.entity.FakeEntity
import com.typewritermc.engine.paper.entry.entity.PositionProperty
import com.typewritermc.engine.paper.entry.entity.entityShowRange
import com.typewritermc.engine.paper.entry.entries.EntityProperty
import com.typewritermc.engine.paper.logger
import btcrenaud.corpse.utils.CorpseDiagnostics
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

        // Drop viewers that went offline, changed world or walked out of range.
        entities.entries.removeIf { (playerId, view) ->
            val player = Bukkit.getPlayer(playerId)
            val stillSees = player != null &&
                player.isOnline &&
                (position.distanceSquared(player.location) ?: Double.MAX_VALUE) <= rangeSquared
            if (!stillSees) runCatching { view.root.dispose() }
            !stillSees
        }

        // Spawn for viewers that came into range.
        for (player in Bukkit.getOnlinePlayers()) {
            val distance = position.distanceSquared(player.location) ?: continue
            if (distance > rangeSquared) continue
            if (entities.containsKey(player.uniqueId)) continue
            spawnFor(player)?.let { entities[player.uniqueId] = View(it) }
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

    private fun spawnFor(player: Player): FakeEntity? = runCatching {
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
        // a throw here leaves no entity and no message at all.
        logger.warning("[Corpse] Failed to spawn corpse for ${player.name}: ${it.stackTraceToString()}")
    }.getOrNull()
}
