package btcrenaud.corpse.api

import btcrenaud.corpse.manager.CorpseManager
import org.bukkit.Location
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * What other extensions may ask of Corpse. Looked up by name and called by reflection (the Dungeon extension does, with no build
 * dependency on this one), so the signatures here only use JDK and Bukkit types and are pinned by `CorpseApiContractTest`.
 */
object CorpseApi {
    /**
     * Brings the corpses out of the world called [worldName] before it is thrown away: the contents go to an online owner, the body
     * of an offline one is moved to [fallback]. See [CorpseManager.evacuateWorld].
     */
    @JvmStatic
    fun evacuateWorld(worldName: String, fallback: Location): CompletableFuture<Int> = CorpseManager.evacuateWorld(worldName, fallback)

    /**
     * Moves the stored corpses of the worlds with these uids, which are not loaded (a killed server left them), to [fallback] before the
     * worlds are deleted. See [CorpseManager.evacuateDormant].
     */
    @JvmStatic
    fun evacuateDormant(worldUids: Collection<UUID>, fallback: Location): CompletableFuture<Int> = CorpseManager.evacuateDormant(worldUids, fallback)
}
