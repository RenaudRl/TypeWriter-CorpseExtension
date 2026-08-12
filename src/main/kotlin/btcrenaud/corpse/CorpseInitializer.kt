package btcrenaud.corpse

import com.typewritermc.core.extension.Initializable
import btcrenaud.corpse.gui.CorpseGUI
import btcrenaud.corpse.listener.CorpseDeathListener
import btcrenaud.corpse.manager.CorpseManager
import btcrenaud.corpse.utils.CorpseReflection
import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.engine.paper.logger
import com.typewritermc.engine.paper.plugin

// Must be Typewriter's own @Singleton: that is what the KSP processor scans to register the
// initializer. With javax.inject.Singleton the extension loaded its entries but nothing ever ran —
// no death listener, no manager, no tick.
@Singleton
object CorpseInitializer : Initializable {

    override suspend fun initialize() {
        CorpseDeathListener.initialize(plugin)
        CorpseGUI.initialize(plugin)
        CorpseManager.initialize()

        val backends = CorpseReflection.getAvailableBackends()
        if (backends.isEmpty()) {
            // Not fatal: without a backend the corpse still holds the loot and can be looted, it
            // just has no model to render.
            logger.warning(
                "[Corpse] No entity backend detected. Corpses will hold loot but render as a plain " +
                    "player entity at best. Install one of: EntityExtension, ModelEngine, BetterModel, " +
                    "BTC Mob NPC, MythicMobs NPC."
            )
        } else {
            logger.info("[Corpse] Ready with entity backend(s): ${backends.joinToString(", ")}")
        }
    }

    override suspend fun shutdown() {
        CorpseDeathListener.shutdown()
        CorpseGUI.shutdown()
        CorpseManager.shutdown()
        logger.info("[Corpse] Shutdown complete")
    }
}
