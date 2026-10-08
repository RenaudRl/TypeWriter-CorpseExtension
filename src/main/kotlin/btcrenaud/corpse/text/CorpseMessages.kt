package btcrenaud.corpse.text

import com.typewritermc.engine.paper.extensions.placeholderapi.parsePlaceholders
import com.typewritermc.engine.paper.utils.asMini
import net.kyori.adventure.text.Component
import org.bukkit.OfflinePlayer
import org.bukkit.command.CommandSender

/**
 * Turns an admin-written template into what [this] reads: placeholders of the corpse first (see
 * [CorpseText.fill] for [values] and [trusted]), then PlaceholderAPI for the reader, then MiniMessage.
 */
fun CommandSender.corpseComponent(
    template: String,
    values: Map<String, String> = emptyMap(),
    trusted: Map<String, String> = emptyMap(),
): Component = CorpseText.fill(template, values, trusted).parsePlaceholders(this as? OfflinePlayer).asMini(this)

fun CommandSender.sendCorpseText(template: String, values: Map<String, String> = emptyMap()) {
    // A blank template is an admin choosing to say nothing: an empty chat line would still show.
    if (template.isBlank()) return
    sendMessage(corpseComponent(template, values))
}
