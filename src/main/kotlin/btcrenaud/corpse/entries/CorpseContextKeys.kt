package btcrenaud.corpse.entries

import com.typewritermc.core.extension.annotations.KeyType
import com.typewritermc.core.interaction.EntryContextKey
import com.typewritermc.core.utils.point.Position
import kotlin.reflect.KClass

/**
 * Data a corpse event publishes to whatever it triggers.
 *
 * Without these the triggered entries had no idea which corpse fired them, so a quest or message
 * could not mention the owner, the loot or where it happened.
 */
enum class CorpseContextKeys(override val klass: KClass<*>) : EntryContextKey {
    @KeyType(String::class)
    OWNER_NAME(String::class),

    @KeyType(String::class)
    OWNER_UUID(String::class),

    @KeyType(Int::class)
    EXPERIENCE(Int::class),

    @KeyType(Int::class)
    ITEM_COUNT(Int::class),

    @KeyType(Position::class)
    CORPSE_POSITION(Position::class),
}
