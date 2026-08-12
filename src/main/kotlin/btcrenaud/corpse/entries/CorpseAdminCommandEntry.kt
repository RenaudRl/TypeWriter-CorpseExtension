package btcrenaud.corpse.entries

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import btcrenaud.corpse.manager.CorpseExpireReason
import btcrenaud.corpse.manager.CorpseManager
import com.typewritermc.engine.paper.command.dsl.command
import com.typewritermc.engine.paper.command.dsl.sender
import com.typewritermc.engine.paper.command.dsl.withPermission
import com.typewritermc.engine.paper.entry.entries.CustomCommandEntry
import com.typewritermc.engine.paper.utils.msg
import io.papermc.paper.command.brigadier.CommandSourceStack

@Entry("corpse_admin_command", "Admin command for corpses", Colors.RED, "mdi:console")
/**
 * Registers a staff command to inspect and clear the corpses currently in the world.
 *
 * ## How could this be used?
 * Check what a player left behind before helping them recover it, or clear corpses stuck in a
 * world that is about to be reset.
 */
class CorpseAdminCommandEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Command to register, without the leading slash.")
    val command: String = "corpses",
    @Help("Permission required to run it.")
    val permission: String = "typewriter.corpse.admin",
) : CustomCommandEntry {

    @Suppress("UnstableApiUsage")
    override fun command() = command<CommandSourceStack>(command) {
        withPermission(permission)

        literal("list") {
            executes {
                val corpses = CorpseManager.activeCorpses()
                if (corpses.isEmpty()) {
                    sender.msg("<gray>No corpses in the world.")
                    return@executes
                }
                sender.msg("<red>☠ <gray>${corpses.size} corpse(s):")
                corpses.forEach { corpse ->
                    val loc = corpse.location
                    val items = corpse.inventory.count { !it.type.isAir }
                    sender.msg(
                        "<gray>- <white>${corpse.playerName}</white> " +
                            "at <white>${loc.blockX}, ${loc.blockY}, ${loc.blockZ}</white> " +
                            "in <white>${loc.world?.name ?: "unknown"}</white> " +
                            "(<yellow>$items</yellow> items, <yellow>${corpse.experience}</yellow> xp)"
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
                sender.msg("<green>Removed ${corpses.size} corpse(s).")
            }
        }
    }
}
