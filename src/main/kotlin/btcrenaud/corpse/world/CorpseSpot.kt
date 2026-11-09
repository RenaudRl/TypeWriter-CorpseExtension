package btcrenaud.corpse.world

/** Where a corpse is put when its owner died outside the build height. */
object CorpseSpot {

    /**
     * The height to place a corpse at, given where its owner died.
     *
     * A death inside the world's height keeps its height. A death below [minY] (the void) or at or
     * above [maxY] leaves a body nobody can reach, so it is moved to the nearest standable block
     * inside the world, searching from the edge towards the middle of the world. When the whole
     * column has none, the height is clamped to the world's bounds: the corpse then sits at the
     * point of death, as close as the world allows.
     *
     * @param isStandable whether a body can lie at that block height of the death column.
     */
    fun resolveY(y: Double, minY: Int, maxY: Int, isStandable: (Int) -> Boolean): Double {
        if (y >= minY && y < maxY) return y
        val range = if (y < minY) minY until maxY else (maxY - 1) downTo minY
        range.firstOrNull(isStandable)?.let { return it.toDouble() }
        return y.coerceIn(minY.toDouble(), maxY - 1.0)
    }
}
