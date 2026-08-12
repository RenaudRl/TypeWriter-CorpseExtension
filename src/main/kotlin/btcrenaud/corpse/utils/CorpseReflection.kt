package btcrenaud.corpse.utils

/**
 * Detects which model backends are installed.
 *
 * Class names are checked against the classes those extensions actually ship. The previous list
 * held two wrong names — `com.typewritermc.btcmobsnpc.BTCMobNpcExtension` and a garbled
 * `com.typewritermc.BtcMobsnpc.BtcMobsNpcExtension` — so those backends always reported absent.
 */
object CorpseReflection {

    private val BACKENDS = linkedMapOf(
        "EntityExtension (player NPC)" to "com.typewritermc.entity.entries.entity.WrapperFakeEntity",
        "ModelEngine" to "entries.entity.definition.ModelEngineDefinition",
        "BetterModel" to "entries.entity.definition.BetterModelDefinition",
        "BTC Mob NPC" to "com.typewritermc.btcmobs.npc.BTCMobNpcExtension",
        "MythicMobs NPC" to "com.typewritermc.mythicmobsnpc.MythicMobsNpcExtension",
    )

    private fun isLoaded(className: String): Boolean =
        runCatching { Class.forName(className) }.isSuccess

    /** Human-readable names of the backends available on this server. */
    fun getAvailableBackends(): List<String> =
        BACKENDS.filterValues { isLoaded(it) }.keys.toList()
}
