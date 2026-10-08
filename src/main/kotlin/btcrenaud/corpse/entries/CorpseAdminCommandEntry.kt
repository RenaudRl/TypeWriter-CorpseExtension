package btcrenaud.corpse.entries

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.extension.annotations.Colored
import com.typewritermc.core.extension.annotations.Default
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.Placeholder
import btcrenaud.corpse.manager.CorpseExpireReason
import btcrenaud.corpse.manager.CorpseManager
import btcrenaud.corpse.text.sendCorpseText
import com.typewritermc.engine.paper.command.dsl.command
import com.typewritermc.engine.paper.command.dsl.sender
import com.typewritermc.engine.paper.command.dsl.withPermission
import com.typewritermc.engine.paper.entry.entries.CustomCommandEntry
import io.papermc.paper.command.brigadier.CommandSourceStack

@Entry("corpse_admin_command", "Admin command for corpses", Colors.RED, "mdi:console")
/**
 * Registers a staff command to inspect and clear the corpses currently on the server.
 *
 * ## How could this be used?
 * Check what a player left behind before helping them recover it, or clear corpses stuck in a
 * world that is about to be reset.
 */
class CorpseAdminCommandEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Command to register, without the leading slash.")
    @Default("\"corpses\"")
    val command: String = "corpses",
    @Help("Permission required to run it.")
    @Default("\"typewriter.corpse.admin\"")
    val permission: String = "typewriter.corpse.admin",
    @Help("Reply to the list subcommand when no corpse exists. Leave empty for no reply.")
    @Colored
    @Placeholder
    @Default("\"" + DEFAULT_HEADER + "<gray>No corpses on the server.\"")
    val noCorpsesMessage: String = DEFAULT_HEADER + "<gray>No corpses on the server.",
    @Help("First line of the list subcommand. Placeholder: <count>, the number of corpses. Leave empty for no line.")
    @Colored
    @Placeholder
    @Default("\"" + DEFAULT_HEADER + "<red>☠ <gray><count> corpse(s):\"")
    val listHeaderMessage: String = DEFAULT_HEADER + "<red>☠ <gray><count> corpse(s):",
    @Help(
        "One line per corpse in the list subcommand. Placeholders: <player> the dead player, " +
            "<x> <y> <z> and <world> where the corpse lies, <items> the stacks it holds, <xp> the experience it holds."
    )
    @Colored
    @Placeholder
    @Default(
        "\"<gray>- <white><player></white> at <white><x>, <y>, <z></white> in <white><world></white> " +
            "(<yellow><items></yellow> items, <yellow><xp></yellow> xp)\""
    )
    val listEntryMessage: String =
        "<gray>- <white><player></white> at <white><x>, <y>, <z></white> in <white><world></white> " +
            "(<yellow><items></yellow> items, <yellow><xp></yellow> xp)",
    @Help("Shown in place of <world> when a corpse's world is no longer loaded.")
    @Colored
    @Default("\"unknown\"")
    val unknownWorldLabel: String = "unknown",
    @Help("Reply to the clear subcommand. Placeholder: <count>, the number of corpses removed. Leave empty for no reply.")
    @Colored
    @Placeholder
    @Default("\"" + DEFAULT_HEADER + "<green>Removed <count> corpse(s).\"")
    val clearedMessage: String = DEFAULT_HEADER + "<green>Removed <count> corpse(s).",
) : CustomCommandEntry {

    @Suppress("UnstableApiUsage")
    override fun command() = command<CommandSourceStack>(command) {
        withPermission(permission)

        literal("list") {
            executes {
                val corpses = CorpseManager.activeCorpses()
                if (corpses.isEmpty()) {
                    sender.sendCorpseText(noCorpsesMessage)
                    return@executes
                }
                sender.sendCorpseText(listHeaderMessage, mapOf("count" to corpses.size.toString()))
                corpses.forEach { corpse ->
                    val loc = corpse.location
                    sender.sendCorpseText(
                        listEntryMessage,
                        mapOf(
                            "player" to corpse.playerName,
                            "x" to loc.blockX.toString(),
                            "y" to loc.blockY.toString(),
                            "z" to loc.blockZ.toString(),
                            "world" to (loc.world?.name ?: unknownWorldLabel),
                            "items" to corpse.inventory.count { !it.type.isAir }.toString(),
                            "xp" to corpse.experience.toString(),
                        ),
                    )
                }
            }
        }

        literal("clear") {
            executes {
                val corpses = CorpseManager.activeCorpses()
                // MANUAL, not EXPIRED: staff clearing corpses must not fire the expiry event and
                // hand players a death penalty they did not earn.
                corpses.forEach { CorpseManager.removeCorpse(it.corpseId, CorpseExpireReason.MANUAL) }
                sender.sendCorpseText(clearedMessage, mapOf("count" to corpses.size.toString()))
            }
        }
    }

    companion object {
        /** The engine's own reply header, which `msg()` used to prepend to every reply of this command. */
        const val DEFAULT_HEADER = "<red><bold>Typewriter »<reset><white> "
    }
}
