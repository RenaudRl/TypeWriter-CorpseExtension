package btcrenaud.corpse.world

import java.util.regex.Pattern

/**
 * Which worlds a rule applies to, written the way an admin names them.
 *
 * A world is identified by two strings and a rule matches if it matches either one: the world
 * name (the folder name, e.g. `dgbuild` or `dungeon_procedural_<uuid>`) and the dimension key
 * (e.g. `minecraft:the_nether`). A pattern is compared to the whole string, ignoring case, and
 * understands two wildcards: `*` for any run of characters (including none) and `?` for exactly
 * one. A pattern with no wildcard is therefore an exact name, and `dungeon_*` is a prefix that
 * covers every world a dungeon generates for a run.
 *
 * Kept free of Bukkit so the matching can be tested without a server.
 */
class WorldFilter private constructor(
    private val allowed: List<Regex>,
    private val excluded: List<Regex>,
) {
    /**
     * Whether the world named [worldName], with dimension key [dimensionKey], passes the filter.
     *
     * An exclusion always wins over an allow entry. With no allow entries every world that is not
     * excluded passes, which is what makes an empty filter mean "everywhere".
     */
    fun allows(worldName: String, dimensionKey: String? = null): Boolean {
        val identities = listOfNotNull(worldName, dimensionKey)
        if (excluded.any { rule -> identities.any { rule.matches(it) } }) return false
        return allowed.isEmpty() || allowed.any { rule -> identities.any { rule.matches(it) } }
    }

    companion object {
        /** No restriction: every world passes. */
        val EVERYWHERE: WorldFilter = of(emptyList(), emptyList())

        /**
         * Builds a filter from the lists an admin typed.
         *
         * Blank entries are dropped, so a list holding only a stray empty line is the same as an
         * empty list (everywhere) rather than a list that matches nothing.
         */
        fun of(allowed: List<String>, excluded: List<String>): WorldFilter =
            WorldFilter(compile(allowed), compile(excluded))

        /** Whether [patterns] select the world. An empty list selects every world. */
        fun selects(patterns: List<String>, worldName: String, dimensionKey: String? = null): Boolean =
            of(patterns, emptyList()).allows(worldName, dimensionKey)

        private fun compile(patterns: List<String>): List<Regex> =
            patterns.map { it.trim() }.filter { it.isNotEmpty() }.map(::globToRegex)

        /** Turns a `*` / `?` pattern into a regex, with every other character taken literally. */
        internal fun globToRegex(glob: String): Regex {
            val source = StringBuilder()
            val literal = StringBuilder()

            fun flushLiteral() {
                if (literal.isEmpty()) return
                source.append(Pattern.quote(literal.toString()))
                literal.setLength(0)
            }

            for (character in glob) {
                when (character) {
                    '*' -> { flushLiteral(); source.append(".*") }
                    '?' -> { flushLiteral(); source.append('.') }
                    else -> literal.append(character)
                }
            }
            flushLiteral()

            return Regex(source.toString(), RegexOption.IGNORE_CASE)
        }
    }
}
