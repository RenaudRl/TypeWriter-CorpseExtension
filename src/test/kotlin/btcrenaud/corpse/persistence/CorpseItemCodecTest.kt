package btcrenaud.corpse.persistence

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CorpseItemCodecTest {

    private fun encode(items: List<String>, unreadable: Map<Int, String> = emptyMap()) =
        CorpseItemCodec.encodeTokens(items, { it.isEmpty() }, { it.toByteArray() }, unreadable)

    private fun decode(raw: String) = CorpseItemCodec.decodeTokens(raw, { "" }, { bytes ->
        String(bytes).also { check(it != "bad") { "cannot read" } }
    })

    @Test
    fun `slots keep their index across a round trip`() {
        val decoded = decode(encode(listOf("a", "", "b")))
        assertEquals(listOf("a", "", "b"), decoded.items)
        assertTrue(decoded.unreadable.isEmpty())
    }

    @Test
    fun `an unreadable item is reported with its raw text instead of vanishing`() {
        val bad = Base64.getEncoder().encodeToString("bad".toByteArray())
        val notBase64 = "%%%"
        val decoded = decode("${Base64.getEncoder().encodeToString("a".toByteArray())};$bad;$notBase64")

        assertEquals(listOf("a", "", ""), decoded.items)
        assertEquals(setOf(1, 2), decoded.unreadable.keys)
        assertEquals(bad, decoded.unreadable.getValue(1).token)
        assertEquals(notBase64, decoded.unreadable.getValue(2).token)
    }

    @Test
    fun `the raw text of an unreadable item is written back by the next save`() {
        val bad = Base64.getEncoder().encodeToString("bad".toByteArray())
        val decoded = decode("${Base64.getEncoder().encodeToString("a".toByteArray())};$bad")

        val again = encode(decoded.items, decoded.unreadable.mapValues { it.value.token })
        assertEquals("${Base64.getEncoder().encodeToString("a".toByteArray())};$bad", again)
    }

    @Test
    fun `a blank column is an empty inventory`() {
        assertTrue(decode("").items.isEmpty())
    }
}
