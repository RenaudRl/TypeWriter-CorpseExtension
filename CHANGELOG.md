# Changelog

## 0.1 — 2026-08-12

First release.

- Corpses are rendered once per viewer, like the engine's own entity displays, so everyone nearby
  sees them, with model animation and nameplates ticking on the BetterModel, BTC Mob NPC and
  MythicMobs NPC backends.
- The loot menu is a GUI Extension menu rather than a bare inventory with its own click listener,
  so it inherits the engine's click routing, menu history and extended-inventory projection.
  **The GUI Extension is therefore required.**
- Runs on Folia: all scheduling goes through a Folia-aware scheduler, and item drops, experience
  orbs and particles run on the region owning the corpse.
- The inventory a corpse holds survives a restart, a crash or a reload. Corpses are stored in
  MySQL when available, otherwise in the plugin folder, and restored on startup.
- `onlyOwnerCanLoot` is enforced, and looting is matched against the corpse's own entity id rather
  than any right-click within four blocks.
- A corpse definition is picked per death by world and priority, and its lifetime is resolved once,
  on that corpse.
- The `on_corpse_spawn`, `on_corpse_loot` and `on_corpse_expire` events go through the engine's
  trigger pipeline and publish the corpse's owner, loot and position as context.
- `displayName`, `showDisplayName`, `dropOnInteract`, `renderArmor`, `playDeathAnimation` and
  `deathAnimationName` are all honoured, and most settings are dynamic values, so they can be
  driven by placeholders and facts.
- A client-rendered glow outline with a configurable colour, and corpse sounds sent per nearby
  player instead of world-wide.
- `has_corpse` and `corpse_count` facts, the `corpse_owner_audience` audience, `corpse_waypoint`
  showing distance and direction to the player's own corpse, `corpse_recovery_objective`, and
  `corpse_admin_command` with `list` and `clear` subcommands.
- A loot protection window, after which a corpse opens to everyone.
