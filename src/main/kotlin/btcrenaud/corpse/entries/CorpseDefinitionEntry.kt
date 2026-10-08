package btcrenaud.corpse.entries

import com.github.retrooper.packetevents.protocol.entity.pose.EntityPose
import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import btcrenaud.corpse.world.WorldFilter
import com.typewritermc.core.extension.annotations.Colored
import com.typewritermc.core.extension.annotations.Default
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.OnlyTags
import com.typewritermc.core.extension.annotations.Placeholder
import com.typewritermc.engine.paper.entry.ManifestEntry
import com.typewritermc.engine.paper.entry.entries.ConstVar
import com.typewritermc.engine.paper.entry.entries.EntityDefinitionEntry
import com.typewritermc.engine.paper.entry.entries.Var

enum class CorpseRotationMode {
    /** A random yaw, so a pile of corpses does not look copy-pasted. */
    RANDOM,

    /** Face the direction the killing blow came from. */
    FACE_KILLER,

    /** Keep the direction the player was facing when they died. */
    FACE_FORWARD,
}

/**
 * How the body is posed.
 *
 * Only applies to the plain player model. Model backends pose themselves through their animation.
 */
enum class CorpsePose(val pose: EntityPose) {
    /** Flat on the back, the most readable "dead body" pose. */
    LYING(EntityPose.SLEEPING),

    /** Face down and horizontal, as if the player fell forward. */
    FALLEN(EntityPose.SWIMMING),

    /** The vanilla dying pose. */
    DYING(EntityPose.DYING),

    /** Upright, for servers that prefer a standing ghost. */
    STANDING(EntityPose.STANDING),
}

@Entry("corpse_definition", "Corpse Model Definition", Colors.RED, "mdi:skull-outline")
/**
 * How a corpse looks.
 *
 * Several definitions can coexist: the one with the highest [priority] whose [worlds] filter
 * matches the death location wins. That is what makes a corpse in the nether able to look
 * different from one in the overworld.
 */
class CorpseDefinitionEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Entity definition used for the corpse model. Empty = plain player model wearing the victim's skin.")
    @OnlyTags(
        "entity_definition",
        "modelengine_definition",
        "bettermodel_definition",
        "btcmob_npc_definition",
        "mythicmob_npc_definition"
    )
    val definitionRef: Ref<out EntityDefinitionEntry> = emptyRef(),
    @Help("Name shown above the corpse. Use {player} for the dead player's name.")
    @Colored
    @Placeholder
    @Default("\"" + DEFAULT_DISPLAY_NAME + "\"")
    val displayName: Var<String> = ConstVar(DEFAULT_DISPLAY_NAME),
    @Help("Show the floating name above the corpse.")
    @Default("true")
    val showDisplayName: Var<Boolean> = ConstVar(true),
    @Help("Vertical offset from the death location, in blocks.")
    @Default("0.0")
    val verticalOffset: Var<Double> = ConstVar(0.0),
    @Help("How the corpse is rotated.")
    @Default("RANDOM")
    val rotationMode: CorpseRotationMode = CorpseRotationMode.RANDOM,

    // ─── Pose and animation ────────────────────────────────────
    @Help("Body pose. Only used by the plain player model; model backends pose themselves.")
    @Default("LYING")
    val pose: CorpsePose = CorpsePose.LYING,
    @Help("Play an animation when the corpse appears. Model backends only.")
    @Default("true")
    val playDeathAnimation: Var<Boolean> = ConstVar(true),
    @Help("Animation to play on spawn, by name, on BetterModel / BTC Mob NPC / MythicMobs NPC models.")
    @Default("\"death\"")
    val deathAnimationName: Var<String> = ConstVar("death"),
    @Help("Loop the animation for as long as the corpse exists. Leave off for a one-shot death animation.")
    @Default("true")
    val loopDeathAnimation: Var<Boolean> = ConstVar(true),
    @Help("Playback speed of the animation.")
    @Default("1.0")
    val deathAnimationSpeed: Var<Double> = ConstVar(1.0),

    // ─── Interaction hit box ───────────────────────────────────
    @Help("Width of the clickable box, in blocks.")
    @Default("1.0")
    val interactionWidth: Double = 1.0,
    @Help("Height of the clickable box, in blocks.")
    @Default("1.0")
    val interactionHeight: Double = 1.0,
    @Help("Vertical offset of the clickable box relative to the corpse, in blocks.")
    @Default("0.0")
    val interactionOffset: Double = 0.0,

    // ─── Lifetime and selection ────────────────────────────────
    @Help("Seconds before corpses of this type expire. Overrides the global setting when above 0.")
    @Default("0")
    val overrideDuration: Var<Int> = ConstVar(0),
    @Help(
        "Worlds this definition applies to. Empty = every world. " +
            "Takes the same entries as the world lists of the global settings: a world name, a dimension key, " +
            "or a pattern such as 'dungeon_*'. This picks the look of a corpse; whether a death leaves " +
            "a corpse at all is decided by the global settings."
    )
    val worlds: List<String> = emptyList(),
    @Help("Highest priority wins when several definitions match the death location.")
    @Default("0")
    val priority: Int = 0,
) : ManifestEntry {
    /**
     * Whether this definition may be used for a death in the world named [worldName], whose
     * dimension key is [dimensionKey]. Matching is [WorldFilter]'s.
     */
    fun appliesTo(worldName: String, dimensionKey: String? = null): Boolean =
        WorldFilter.selects(worlds, worldName, dimensionKey)

    companion object {
        /** The name above a corpse; also what a restored corpse shows when its name is placeholder-driven. */
        const val DEFAULT_DISPLAY_NAME = "<red>☠ {player}'s corpse"
    }
}
