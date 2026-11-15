# DivineRPG Extended

An add-on for [DivineRPG](https://modrinth.com/mod/divinerpg) 1.4.2.3 for Minecraft 1.7.10 (Forge 10.13.4.1614). It turns DivineRPG into a connected RPG progression: dimensions open in order, one armor set grows with you through the whole game, and bosses unlock abilities and new merchants.

## Features

- **Boss progression.** New early-game bosses (Carcinus, Snow Glacon, Frost Colossus and the Lich King) with their own lairs. Spawn ranks: DivineRPG mobs start appearing as you defeat bosses (`/drerank` shows the ranks of online players). Only players who have killed The Watcher can enter Arcana.
- **Wanderer armor.** A single armor set is tempered by every dimension (R1-R8) at the Wanderer's Anvil; DivineRPG armor sets become perk donors.
- **Iceika.** Cold, blizzards, the Frozen Throne, Rime Ore.
- **Terraria-style fishing.** 12 fishing rods, baits, zones, crates, Leorna's quests, relics.
- **Arcana merchants.** They arrive as you progress and can be called with the Memory Crystal. Captain Merik reforges weapons, the War General has the Hall of Fame and orders, Lord Vatticus has a handbook and arcana research, Datticon gives furnace roles, Zelus trains pets.
- **Jack o'Man and the Livestock Merchant.** Night trading by stage, accessories made from mob heads, skills for Overworld pets, pet carrying.
- **Accessory slots** like in Terraria, unlocked by bosses.
- **NEI integration** (fishing, crate and merchant tabs) and a creative tab of its own.

## Installation

Put DivineRPG 1.4.2.3 into the `mods` folder next to the add-on (DivineRPG 1.4.1.5 is supported as well). NEI is optional.

The DivineRPG config must have `Vethea Inventory = false`.

## Building

Requires JDK 8.

1. Put into `libs/` (they are not in the repository - these are other authors' mods):
   - `DivineRPG-1.4.2.3.jar`
   - `NotEnoughItems-2.8.160-GTNH.jar`
   - `CodeChickenCore-1.4.22.jar`
2. Run `./gradlew build`. The jar appears in `build/libs/`.

## Versions

Built jars of all versions are in `releases/`: every version is added in its own commit. The sources in the repository match the latest version.

## Commands

| Command | What it does |
|---|---|
| `/drerank` | Divine ranks (0-3) of all online players and the boss that opens the next rank (available to everyone) |
| `/dreboss list\|grant\|revoke <boss\|all> [player]` | view, grant or revoke killed bosses (admins) |
| `/dreinv <player>` | a player's accessories: editable while online, view-only while offline (admins) |
| `/dreblizzard start\|stop` | start or stop the Iceika blizzard, the Wildwood downpour and the Apalachia rockfall (admins) |
| `/dredebug [on\|off\|status]`, `/dredebug <chat\|file\|mobs> <on\|off>` | personal debugging: step-by-step damage, effects, healing and mechanic triggers in chat; actions, keys and state snapshots as CSV in `logs/dre-debug` (admins) |

## License

An unofficial add-on for DivineRPG: it contains no DivineRPG code or assets. You may play it, run it on servers, include it in modpacks and make your own add-ons; you may not re-host it, publish modified versions or copy its code or assets into other projects. Full text: [LICENSE.md](LICENSE.md).

## Audit log for admins

Actions that logging plugins cannot see (they happen in the mod's own storages and mechanics, not in blocks) are written to `logs/dre-audit.log`: accessories (who, whose, what it was and what it became, including edits through `/dreinv`), pet carrying, merchant trades and services (Jack, the workshop master, Merik, the General, Vatticus, Zelus, furnace modules), Leorna's quests, dream gifts, item returns and the Vethea guard. If HawkEye is installed on the server, the same actions go there as OTHER records with the action `DRE ...`: `/hawk search p:Player`. It can be turned off in the config, section `audit`.
