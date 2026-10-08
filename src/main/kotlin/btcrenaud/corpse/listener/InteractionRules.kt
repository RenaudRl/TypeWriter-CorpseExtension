package btcrenaud.corpse.listener

/** Decisions about a click on a corpse that need no server to take. */
internal object InteractionRules {

    /**
     * Whether a packet is a new gesture rather than a repeat of the one just handled.
     *
     * The client repeats the packet while the button is held, and one right-click can yield both
     * `INTERACT` and `INTERACT_AT`. A [cooldownMillis] of zero or less accepts every packet.
     *
     * @param previousMillis time of the last accepted packet from this player, null if none.
     */
    fun isNewGesture(previousMillis: Long?, nowMillis: Long, cooldownMillis: Long): Boolean =
        previousMillis == null || nowMillis - previousMillis >= cooldownMillis

    /**
     * Whether a player is close enough to a corpse for a click on it to count.
     *
     * The interaction packet of a packet-only entity reaches the server unchecked, so a modified
     * client can click a corpse it can merely see. [reach] is the longest accepted distance in
     * blocks; zero or less disables the check.
     *
     * @param distanceSquared squared distance to the corpse, null when they are in different worlds.
     */
    fun isWithinReach(distanceSquared: Double?, reach: Double): Boolean {
        if (distanceSquared == null) return false
        if (reach <= 0.0) return true
        return distanceSquared <= reach * reach
    }
}
