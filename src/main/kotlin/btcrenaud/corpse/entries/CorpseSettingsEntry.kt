package btcrenaud.corpse.entries

import btcrenaud.corpse.world.WorldFilter
import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.extension.annotations.Colored
import com.typewritermc.core.extension.annotations.Default
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.Min
import com.typewritermc.core.extension.annotations.Placeholder
import com.typewritermc.engine.paper.entry.ManifestEntry
import com.typewritermc.engine.paper.entry.entries.ConstVar
import com.typewritermc.engine.paper.entry.entries.Var
import com.typewritermc.engine.paper.utils.Color
import com.typewritermc.engine.paper.utils.Sound
import org.bukkit.Material
import org.bukkit.Particle

/** What happens when a player interacts with a corpse. */
enum class CorpseLootMode {
    /** Open the corpse inventory so items can be taken one at a time. */
    GUI,

    /** Move everything straight into the looter's inventory, dropping only what does not fit. */
    DIRECT,

    /** Drop everything on the ground at the corpse. */
    DROP,
}

@Entry("corpse_settings", "Corpse Global Settings", Colors.RED, "mdi:skull")
/**
 * Server-wide corpse behaviour.
 *
 * Every value is a `Var`, so it can be driven by placeholders and facts instead of being a single
 * constant: protection time can depend on rank, the loot mode can differ per world, and every line
 * of text shown to a player can be rewritten or translated without touching the extension.
 */
class CorpseSettingsEntry(
    override val id: String = "",
    override val name: String = "",

    // ─── Where corpses appear ──────────────────────────────────
    @Help(
        "Worlds where a death leaves a corpse. Empty = every world. " +
            "Outside these worlds a death is vanilla: no corpse, normal drops. " +
            "One entry per world. '*' matches any run of characters and '?' exactly one, so " +
            "'dungeon_*' covers every world a dungeon generates for a run and 'dgbuild' is one named world. " +
            "Each entry is compared, ignoring case, with the world name (its folder name) " +
            "and with its dimension key (for example 'minecraft:the_nether')."
    )
    val allowedWorlds: List<String> = emptyList(),
    @Help(
        "Worlds where a death never leaves a corpse, even if they match the list above. " +
            "Same syntax: 'dungeon_*', 'dgbuild', 'minecraft:the_end'. Empty = no exclusion."
    )
    val excludedWorlds: List<String> = emptyList(),

    // ─── Lifetime and access ───────────────────────────────────
    @Help("Seconds before the corpse disappears. 0 = never expire.")
    @Default("300")
    val duration: Var<Int> = ConstVar(300),
    @Help("If true, only the dead player may ever loot their own corpse.")
    @Default("false")
    val onlyOwnerCanLoot: Var<Boolean> = ConstVar(false),
    @Help("Seconds during which only the owner may loot, after which the corpse opens to everyone. 0 = no protection.")
    @Default("0")
    val ownerProtectionSeconds: Var<Int> = ConstVar(0),

    // ─── Looting ───────────────────────────────────────────────
    @Help("What a right-click does: open the inventory, hand everything over, or drop it on the ground.")
    @Default("GUI")
    val lootMode: CorpseLootMode = CorpseLootMode.GUI,
    @Help(
        "Sneaking while interacting uses the other mode: the GUI when looting is direct, " +
            "and direct looting when the GUI is the default. When the default is DROP, sneaking opens the GUI."
    )
    @Default("true")
    val sneakSwapsLootMode: Var<Boolean> = ConstVar(true),
    @Help(
        "Longest distance in blocks between a player and a corpse for a click on it to count. " +
            "Stops a modified client from looting a corpse it can see but is not near. 0 = no limit."
    )
    @Default("6.0")
    val interactionReach: Var<Double> = ConstVar(6.0),
    @Help(
        "Milliseconds during which further clicks of the same player on a corpse are ignored. " +
            "One click sends several packets; without this the menu would open, or the loot be taken, several times over."
    )
    @Default("250")
    @Min(0)
    val interactionCooldownMillis: Var<Int> = ConstVar(250),
    @Help("Reserve dropped items for the looter, so nobody else can pick them up.")
    @Default("true")
    val reserveDroppedItems: Var<Boolean> = ConstVar(true),
    @Help("Seconds the reservation lasts. 0 = until the item despawns.")
    @Default("30")
    val reserveDroppedItemsSeconds: Var<Int> = ConstVar(30),

    // ─── Appearance ────────────────────────────────────────────
    @Help("Whether the corpse glows.")
    @Default("true")
    val glowEffect: Var<Boolean> = ConstVar(true),
    @Help("Colour of the glow outline.")
    val glowColor: Color = Color.WHITE,
    @Help("Render the armour the player was wearing on the corpse.")
    @Default("true")
    val renderArmor: Var<Boolean> = ConstVar(true),
    @Help("Spawn particles when the corpse appears.")
    @Default("true")
    val spawnParticles: Var<Boolean> = ConstVar(true),
    @Help("Particle spawned at chest height.")
    @Default("DAMAGE_INDICATOR")
    val spawnParticle: Particle = Particle.DAMAGE_INDICATOR,
    @Help("How many of them.")
    @Default("15")
    val spawnParticleCount: Var<Int> = ConstVar(15),
    @Help("Second particle, spawned slightly higher. Set the count to 0 to use a single layer.")
    @Default("SOUL")
    val spawnParticleSecondary: Particle = Particle.SOUL,
    @Help("How many of the second particle.")
    @Default("8")
    val spawnParticleSecondaryCount: Var<Int> = ConstVar(8),
    @Help("Range in blocks within which players hear corpse sounds.")
    @Default("32.0")
    val effectRange: Var<Double> = ConstVar(32.0),
    @Help("Sound played to nearby players when the corpse spawns.")
    val spawnSound: Sound = Sound.EMPTY,
    @Help("Sound played to nearby players when the corpse is looted.")
    val lootSound: Sound = Sound.EMPTY,

    // ─── Messages ──────────────────────────────────────────────
    // Every message accepts MiniMessage and PlaceholderAPI, and an empty one is not sent at all.
    @Help("Tell the dead player where their corpse is when they respawn.")
    @Default("true")
    val notifyOnRespawn: Var<Boolean> = ConstVar(true),
    @Help("Respawn message. Placeholders: <x> <y> <z> <world>. Leave empty for no message.")
    @Colored
    @Placeholder
    @Default("\"<red>☠ <gray>Your corpse is at <white><x>, <y>, <z> <gray>in <white><world>\"")
    val respawnMessage: Var<String> =
        ConstVar("<red>☠ <gray>Your corpse is at <white><x>, <y>, <z> <gray>in <white><world>"),
    @Help("Shown in place of <world> when the corpse's world is no longer loaded.")
    @Colored
    @Placeholder
    @Default("\"unknown\"")
    val unknownWorldLabel: Var<String> = ConstVar("unknown"),
    @Help("Shown when a player may not loot this corpse. Leave empty for no message.")
    @Colored
    @Placeholder
    @Default("\"<red>You cannot loot this corpse.\"")
    val cannotLootMessage: Var<String> = ConstVar("<red>You cannot loot this corpse."),
    @Help("Shown when a player clicks a corpse from farther than the interaction reach. Empty by default: no message.")
    @Colored
    @Placeholder
    @Default("\"\"")
    val outOfReachMessage: Var<String> = ConstVar(""),
    @Help("Shown when the looter's inventory is full and items had to be dropped. Leave empty for no message.")
    @Colored
    @Placeholder
    @Default("\"<red>Your inventory is full.\"")
    val inventoryFullMessage: Var<String> = ConstVar("<red>Your inventory is full."),
    @Help("Shown when the corpse is gone before the interaction completed. Leave empty for no message.")
    @Colored
    @Placeholder
    @Default("\"<red>This corpse is no longer available.\"")
    val corpseGoneMessage: Var<String> = ConstVar("<red>This corpse is no longer available."),

    // ─── Inventory GUI ─────────────────────────────────────────
    @Help("Title of the loot menu. Placeholder: <player> for the dead player's name.")
    @Colored
    @Placeholder
    @Default("\"<red>☠ <player>'s corpse\"")
    val guiTitle: Var<String> = ConstVar("<red>☠ <player>'s corpse"),
    @Help("Label of the button that takes everything.")
    @Colored
    @Placeholder
    @Default("\"<green>Take everything\"")
    val guiLootAllLabel: Var<String> = ConstVar("<green>Take everything"),
    @Help("Label of the close button.")
    @Colored
    @Placeholder
    @Default("\"<red>Close\"")
    val guiCloseLabel: Var<String> = ConstVar("<red>Close"),
    @Help("Label of the previous page button, shown only when the corpse holds more than one page.")
    @Colored
    @Placeholder
    @Default("\"<yellow>Previous page\"")
    val guiPreviousPageLabel: Var<String> = ConstVar("<yellow>Previous page"),
    @Help("Label of the next page button, shown only when the corpse holds more than one page.")
    @Colored
    @Placeholder
    @Default("\"<yellow>Next page\"")
    val guiNextPageLabel: Var<String> = ConstVar("<yellow>Next page"),
    @Help("Line showing the experience the corpse holds. Placeholder: <experience>.")
    @Colored
    @Placeholder
    @Default("\"<gray>Experience: <yellow><experience>\"")
    val guiExperienceLabel: Var<String> = ConstVar("<gray>Experience: <yellow><experience>"),
    @Help("Line showing the current page. Placeholders: <page> and <pages>.")
    @Colored
    @Placeholder
    @Default("\"<gray>Page <white><page><gray>/<white><pages>\"")
    val guiPageLabel: Var<String> = ConstVar("<gray>Page <white><page><gray>/<white><pages>"),
    @Help("Item filling the menu slots that carry nothing.")
    @Default("\"BLACK_STAINED_GLASS_PANE\"")
    val guiFillerMaterial: Material = Material.BLACK_STAINED_GLASS_PANE,
    @Help("Item of the button that takes everything.")
    @Default("\"CHEST\"")
    val guiLootAllMaterial: Material = Material.CHEST,
    @Help("Item of the close button.")
    @Default("\"BARRIER\"")
    val guiCloseMaterial: Material = Material.BARRIER,
    @Help("Item of the previous and next page buttons.")
    @Default("\"ARROW\"")
    val guiPageMaterial: Material = Material.ARROW,

    // ─── Diagnostics ───────────────────────────────────────────
    @Help("Log every corpse spawn and every interact packet to the console. Turn on only while diagnosing a corpse that does not react to clicks — it is one line per click.")
    @Default("false")
    val debugInteractions: Boolean = false,
) : ManifestEntry {

    /** The rule deciding in which worlds a death leaves a corpse. */
    fun worldFilter(): WorldFilter = WorldFilter.of(allowedWorlds, excludedWorlds)
}
