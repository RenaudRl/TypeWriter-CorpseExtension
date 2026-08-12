package btcrenaud.corpse.entity

import com.typewritermc.core.utils.point.Vector
import com.typewritermc.engine.paper.entry.entity.EntityCreator
import com.typewritermc.engine.paper.entry.entity.FakeEntity
import com.typewritermc.engine.paper.entry.entries.ConstVar
import com.typewritermc.engine.paper.logger
import com.typewritermc.entity.entries.entity.custom.HitBoxEntity
import com.typewritermc.entity.entries.entity.minecraft.PlayerEntity
import org.bukkit.entity.Player

/**
 * Creator used when a corpse has no entity definition configured.
 *
 * Builds EntityExtension's own [PlayerEntity], so the skin, armour and pose the corpse pushes are
 * actually applied. The original fallback overrode `applyProperty` with an empty body, which
 * silently swallowed every property including the skin.
 */
class FallbackCorpseCreator(private val displayName: String) : EntityCreator {
    override fun create(player: Player): FakeEntity = PlayerEntity(player, ConstVar(displayName))
}

/**
 * Wraps a corpse model in an interaction hit box.
 *
 * A corpse is not clickable on its own. Lying poses collapse the player hitbox to roughly a fifth
 * of a block, and display-entity backends such as BetterModel have no hitbox at all — which is
 * exactly the case [HitBoxEntity] exists for. Without this the body renders but right-clicking it
 * does nothing.
 *
 * [HitBoxEntity.contains] matches both the box and the model, so either one being clicked resolves
 * to the corpse.
 */
class CorpseHitBoxCreator(
    private val delegate: EntityCreator,
    private val width: Double,
    private val height: Double,
    private val verticalOffset: Double,
) : EntityCreator {
    override fun create(player: Player): FakeEntity = HitBoxEntity(
        player,
        delegate.create(player),
        Vector(0.0, verticalOffset, 0.0),
        width,
        height,
    )
}

/**
 * Plays a named animation on a model-backed corpse.
 *
 * BetterModel, BTC Mob NPC and MythicMobs NPC each expose `playAnimation` on their entity, but none
 * of them share a common interface and none is a hard dependency of this extension. Calling it
 * reflectively is what lets one animation name work across all three without forcing any of them to
 * be installed.
 *
 * Silent by design when the backend has no such method: the plain player model is posed instead.
 */
/**
 * The model inside a corpse entity, or the entity itself when it is not wrapped.
 *
 * [HitBoxEntity] forwards properties and disposal to what it wraps, but not `tick()` — neither it
 * nor its `WrapperFakeEntity` parent override the engine's empty default. Ticking only the wrapper
 * therefore freezes the model it holds, which is what drives animation and nameplates on every
 * model backend. Anything that must reach the model itself goes through here.
 */
fun FakeEntity.corpseModel(): FakeEntity {
    if (this !is HitBoxEntity) return this
    return runCatching {
        HitBoxEntity::class.java.getDeclaredField("baseEntity")
            .apply { isAccessible = true }
            .get(this) as? FakeEntity
    }.getOrNull() ?: this
}

object CorpseAnimator {

    fun play(entity: FakeEntity, animation: String, speed: Double, loop: Boolean): Boolean {
        if (animation.isBlank()) return false
        val target = entity.corpseModel()

        // Signatures seen across the three backends, most specific first.
        val attempts = listOf(
            listOf(String::class.java, Double::class.java, Boolean::class.java, Boolean::class.java)
                to arrayOf<Any>(animation, speed, true, loop),
            listOf(String::class.java, Double::class.java, Boolean::class.java)
                to arrayOf<Any>(animation, speed, true),
            listOf(String::class.java) to arrayOf<Any>(animation),
        )

        for ((types, args) in attempts) {
            val method = runCatching {
                target::class.java.getMethod("playAnimation", *types.toTypedArray())
            }.getOrNull() ?: continue
            val played = runCatching { method.invoke(target, *args) }
                .onFailure { logger.warning("[Corpse] Animation '$animation' failed: ${it.cause?.message ?: it.message}") }
                .getOrNull()
            return played != false
        }
        return false
    }
}
