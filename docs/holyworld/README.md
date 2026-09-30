# HolyWorld features: in-game check and what still needs real samples

These screenshots come from a local 1.21.11 server started with `-Dskirmish.holyworld=true`, which makes the mod
treat any server as HolyWorld. The events and schedule panels show live data from `api.holyworld.me`. The
auction chest is a plain chest named «Аукцион [1/3]» with lore written in the format the parser expects.
The КТ timer uses the local fallback here, because a vanilla server has no HolyWorld sidebar.

| Screenshot | What it shows |
|---|---|
| `events-schedule.png` | Events panel (Lite by server, Prime countdowns) and the schedule panel |
| `logout-banner.png` | «Режим шалкера» banner with 3 shulkers in the inventory |
| `disconnect-confirm.png` | «Точно выйти?» in front of the pause screen's «Отключиться» |
| `cooldowns.png` | Own cooldown row after an ender pearl |
| `combat-tag-tier.png` | КТ panel (local timer), fight panel, target card with the «Infinity 4/4» tier chip |
| `auction-chips.png` | Per-item price chips, cheapest lot ringed, dearer lot red |
| `auction-tooltip.png` | Tooltip lines: per item, usual price, cheapest on page |
| `anvil-lite.png` | AnvilCalc with the HolyWorld Lite rules picked automatically |

## Network

- **HTTP:** GET requests to `https://api.holyworld.me` only, and only while connected to HolyWorld with
  «Данные HolyWorld» on. Responses are cached for at least 30 s, and failures back off for 1, 2, 5, then 10 min.
- **Feature Control:** a `liteapi:feature-control` `checkFeatures` request on join (at most once per 10 s). Any
  feature id the server blocks is hidden from the menu and HUD.
- **Nothing else is sent:** no other packets, and no automation of player actions.

## Confirmed on the real server (capture of 2026-09-24, Lite anarchy)

- **Feature Control:** the anarchy server answers `checkFeatures` in about 60 ms (empty blocklist). The login lobby does not
  answer. The raw UTF-8 JSON framing works.
- **Damage packets:** every hit arrives as `minecraft:generic` with no attacker. Fights, kills and my killer are
  therefore inferred:
  - my click on the entity that took the hit, within 700 ms;
  - the latest arm swing of a player within 6 blocks of me;
  - the killer's name in the death line «▶ Вы были убиты игроком Nick на координатах x y z».
  Tested locally with `-Dskirmish.debug.anonymousDamage=true`, which strips the attacker the same way.
- **Other players' equipment:** it is a stand-in: every item has count 64 and damage 123. Durability is shown as "no
  data" for such stacks; enchantments come through.
- **КТ:**
  - «▶ Вы вошли в режим PVP!» and «▶ Вы вышли из режима PVP!» in chat;
  - the boss bar «Режим PVP - 30 секунд»;
  - one sidebar line per opponent, `▍ Nick  (27 ⌚) 16/20 ❤`, whose seconds restart at 30 on every hit;
  - «Не выходите / из игры 30 секунд.» is a static hint, not a timer.
- **Chat:**
  - event coordinates: «▶ Золотая лихорадка уже на координатах 0 63 0»;
  - vote countdown: «▶ Ближайшее голосование за мероприятие будет проводиться через 39 мин., 21 сек.»;
  - combat logging: «▶ Игрок Nick покинул игру во время ПВП и был наказан.».
- **Auction:** the window title is «Аукцион (1/149)». Seller and price lines parse on most lots.
- **Scoreboard:** HolyWorld keeps its sidebar on the right at all times. HUD elements left at their default place now move out
  of it, and the schedule sits under the events list.

## Still unknown (turn on the module's «Отладочный лог» on the real server)

Each module writes unrecognised candidate lines to `config/skirmish/debug.log`.

1. Whether the КТ boss bar counts down. The PvP log now dumps the whole board and the boss bars once per tag.
2. Why some auction lots don't parse. The first five per page are now logged with their lore.
3. Donor tier item names, custom enchantment lore, spheres and talismans. No donor gear was in view in the first capture.
4. What an Element item looks like, and the shulker/element mode action-bar text.
