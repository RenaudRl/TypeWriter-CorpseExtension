package btcrenaud.corpse.text

import kotlin.test.Test
import kotlin.test.assertEquals

class CorpseTextTest {

    @Test
    fun `angle and curly placeholders are both filled`() {
        assertEquals("a Steve b Steve", CorpseText.fill("a <player> b {player}", mapOf("player" to "Steve")))
    }

    @Test
    fun `other tags are left for MiniMessage`() {
        assertEquals("<red>Steve</red>", CorpseText.fill("<red><player></red>", mapOf("player" to "Steve")))
    }

    @Test
    fun `unknown placeholders are kept as written`() {
        assertEquals("<nope> {nope}", CorpseText.fill("<nope> {nope}", mapOf("player" to "Steve")))
    }

    @Test
    fun `a value cannot inject a tag`() {
        val filled = CorpseText.fill("<player>", mapOf("player" to "<click:run_command:/op x>hi"))
        assertEquals(CorpseText.escape("<click:run_command:/op x>hi"), filled)
        assert(!filled.startsWith("<click"))
    }

    @Test
    fun `a value containing a placeholder is not expanded again`() {
        assertEquals(CorpseText.escape("<world>"), CorpseText.fill("<player>", mapOf("player" to "<world>", "world" to "W")))
    }

    @Test
    fun `trusted values are inserted verbatim`() {
        assertEquals("<gold>↑", CorpseText.fill("<direction>", emptyMap(), mapOf("direction" to "<gold>↑")))
    }

    @Test
    fun `values win over trusted when both name a key`() {
        assertEquals("v", CorpseText.fill("<k>", mapOf("k" to "v"), mapOf("k" to "t")))
    }

    @Test
    fun `no values returns the template untouched`() {
        assertEquals("<player>", CorpseText.fill("<player>", emptyMap()))
    }

    @Test
    fun `dollar and backslash in a value are literal`() {
        assertEquals("a\$1\\b", CorpseText.fill("<v>", mapOf("v" to "a\$1\\b")))
    }
}