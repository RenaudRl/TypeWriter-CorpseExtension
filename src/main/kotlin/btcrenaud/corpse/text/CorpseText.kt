package btcrenaud.corpse.text

import net.kyori.adventure.text.minimessage.MiniMessage

/**
 * Fills the placeholders of an admin-written message.
 *
 * Every text a player or staff member reads is a field an admin can rewrite, so the code can only
 * hand it values to put in. A value can come from a player (a name) or from a world name, and the
 * result is then parsed as MiniMessage, so each value is escaped first: a name that reads
 * `<click:run_command:...>` must show as text, not become a clickable tag.
 */
object CorpseText {

    private val PLACEHOLDER = Regex("""<(\w+)>|\{(\w+)\}""")

    /**
     * Replaces `<key>` and `{key}` for every key of [values] and [trusted]; any other tag is left
     * untouched so MiniMessage still sees `<red>`, `<bold>` and the like.
     *
     * [values] are escaped, so they show up as plain text whatever they contain. [trusted] are
     * inserted as written, for text that an admin authored and may style (the arrow symbols of the
     * waypoint); never put anything a player typed there. [values] win when a key is in both.
     *
     * One pass over [template]: a replaced value is never scanned again, so a value that happens to
     * contain `<other>` cannot be expanded into another placeholder.
     */
    fun fill(
        template: String,
        values: Map<String, String>,
        trusted: Map<String, String> = emptyMap(),
    ): String {
        if (values.isEmpty() && trusted.isEmpty()) return template
        return PLACEHOLDER.replace(template) { match ->
            val key = match.groupValues[1].ifEmpty { match.groupValues[2] }
            values[key]?.let(::escape) ?: trusted[key] ?: match.value
        }
    }

    internal fun escape(value: String): String = MiniMessage.miniMessage().escapeTags(value)
}
