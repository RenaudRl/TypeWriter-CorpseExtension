package btcrenaud.corpse.manager

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EvacuationFlowTest {
    private val long = 5_000L

    private fun ok(value: Boolean) = CompletableFuture.completedFuture(value)

    private fun run(
        corpses: List<String>,
        hand: (String) -> CompletableFuture<Boolean>?,
        move: (String) -> CompletableFuture<Boolean>,
        timeoutMillis: Long = long,
        report: (Int, Int) -> Unit = { _, _ -> },
    ): CompletableFuture<Int> = EvacuationFlow.run(corpses, hand, move, timeoutMillis, report)

    @Test
    fun `nothing to bring out completes with zero`() {
        assertEquals(0, run(emptyList(), { null }, { ok(true) }).get(1, TimeUnit.SECONDS))
    }

    @Test
    fun `a corpse handed over to its owner is counted and not moved`() {
        val moved = mutableListOf<String>()
        val out = run(listOf("a", "b"), { ok(true) }, { moved += it; ok(true) }).get(1, TimeUnit.SECONDS)
        assertEquals(2, out)
        assertTrue(moved.isEmpty())
    }

    @Test
    fun `a corpse without an online owner is moved`() {
        val moved = mutableListOf<String>()
        assertEquals(1, run(listOf("a"), { null }, { moved += it; ok(true) }).get(1, TimeUnit.SECONDS))
        assertEquals(listOf("a"), moved)
    }

    @Test
    fun `a hand-over that said no, failed or threw falls back to the move`() {
        val moved = mutableListOf<String>()
        val out = run(
            listOf("no", "failed", "threw"),
            { corpse ->
                when (corpse) {
                    "no" -> ok(false)
                    "failed" -> CompletableFuture.failedFuture(IllegalStateException("boom"))
                    else -> error("boom")
                }
            },
            { moved += it; ok(true) },
        ).get(1, TimeUnit.SECONDS)
        assertEquals(3, out)
        assertEquals(listOf("no", "failed", "threw"), moved)
    }

    @Test
    fun `only the corpses whose last step answered true are counted, and the rest is reported once`() {
        var report: Pair<Int, Int>? = null
        val out = run(
            listOf("a", "b", "c"),
            { null },
            { corpse -> if (corpse == "b") ok(false) else if (corpse == "c") CompletableFuture.failedFuture(IllegalStateException()) else ok(true) },
            report = { notOut, total -> report = notOut to total },
        ).get(1, TimeUnit.SECONDS)
        assertEquals(1, out)
        assertEquals(2 to 3, report)
    }

    @Test
    fun `a move that throws counts as not out`() {
        assertEquals(0, run(listOf("a"), { null }, { error("boom") }).get(1, TimeUnit.SECONDS))
    }

    @Test
    fun `a step that never answers ends at the timeout with the others counted`() {
        var report: Pair<Int, Int>? = null
        val out = run(
            listOf("quick", "stuck"),
            { corpse -> if (corpse == "stuck") CompletableFuture<Boolean>() else ok(true) },
            { ok(true) },
            timeoutMillis = 50,
            report = { notOut, total -> report = notOut to total },
        ).get(2, TimeUnit.SECONDS)
        assertEquals(1, out)
        assertEquals(1 to 2, report)
    }

    @Test
    fun `nothing is reported when every corpse is out`() {
        var report: Pair<Int, Int>? = null
        run(listOf("a"), { ok(true) }, { ok(true) }, report = { n, t -> report = n to t }).get(1, TimeUnit.SECONDS)
        assertNull(report)
    }
}
