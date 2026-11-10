package btcrenaud.corpse.api

import org.bukkit.Location
import java.lang.reflect.Modifier
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Dungeon extension calls [CorpseApi.evacuateWorld] by name, without a build dependency on Corpse (see its
 * `CorpseEvacuation`): renaming or reshaping it must fail here, not at the first dungeon that ends on a server.
 */
class CorpseApiContractTest {

    @Test
    fun `the evacuation entry point keeps the name and the static signature the Dungeon extension looks up`() {
        val method = Class.forName("btcrenaud.corpse.api.CorpseApi").getMethod("evacuateWorld", String::class.java, Location::class.java)
        assertEquals(CompletableFuture::class.java, method.returnType)
        assertTrue(Modifier.isStatic(method.modifiers))
    }

    @Test
    fun `the dormant evacuation entry point keeps the name and the static signature the Dungeon extension looks up`() {
        val method = Class.forName("btcrenaud.corpse.api.CorpseApi").getMethod("evacuateDormant", Collection::class.java, Location::class.java)
        assertEquals(CompletableFuture::class.java, method.returnType)
        assertTrue(Modifier.isStatic(method.modifiers))
    }
}
