package btcrenaud.corpse.entity

/**
 * What a corpse holds, behind one lock.
 *
 * Clicks on a corpse can come from several regions at once. Every way of taking something out goes
 * through this lock, so an item has exactly one taker: the "take everything" button empties the
 * corpse here, in one step, instead of copying it first and handing the copy out while another
 * player takes a slot of the same corpse.
 *
 * @param isEmpty whether a slot holds nothing.
 * @param empty the value that marks an emptied slot; slots keep their index.
 * @param copy detaches a stack from the caller, so nobody edits the corpse's own.
 */
class CorpseContents<T : Any>(
    items: List<T>,
    experience: Int,
    private val isEmpty: (T) -> Boolean,
    private val empty: () -> T,
    private val copy: (T) -> T,
) {
    private val slots = items.map(copy).toMutableList()
    private var experience = experience

    val items: List<T> get() = synchronized(slots) { slots.map(copy) }

    val heldExperience: Int get() = synchronized(slots) { experience }

    fun remove(slot: Int): T? = synchronized(slots) {
        if (slot !in slots.indices) return@synchronized null
        val item = slots[slot]
        if (isEmpty(item)) return@synchronized null
        slots[slot] = empty()
        item
    }

    fun takeAll(): Map<Int, T> = synchronized(slots) {
        val taken = linkedMapOf<Int, T>()
        for (index in slots.indices) {
            if (isEmpty(slots[index])) continue
            taken[index] = slots[index]
            slots[index] = empty()
        }
        taken
    }

    fun restore(slot: Int, item: T): Boolean = synchronized(slots) {
        if (slot !in slots.indices || !isEmpty(slots[slot])) return@synchronized false
        slots[slot] = copy(item)
        true
    }

    fun isEmpty(): Boolean = synchronized(slots) { slots.all(isEmpty) }

    fun takeExperience(): Int = synchronized(slots) { experience.also { experience = 0 } }

    fun restoreExperience(amount: Int) = synchronized(slots) { experience += amount.coerceAtLeast(0) }
}
