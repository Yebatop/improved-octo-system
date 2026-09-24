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

## Formats that are guesses (turn on the module's «Отладочный лог» on the real server)

Each module writes unrecognised candidate lines to `config/skirmish/debug.log`.

1. **КТ (PvP module):** the sidebar line with the timer (`/board full` and `/board onlypvp`), the opponent lines,
   and any PvP boss bar. Also whether трапка, стан and golden apples arrive as vanilla item cooldowns.
2. **Events:**
   - sub-server names in the sidebar, the tab list and join messages;
   - the Lite/Prime markers;
   - vote announcements;
   - how `/event` output writes coordinates.
3. **Profile:**
   - donor tier item names (Stinger, Eternity, Infinity, a weapon);
   - lore of custom enchantments (Непробиваемый, Цербер);
   - sphere, talisman and rune lore;
   - «сомнительная» books on Prime;
   - one real Lite anvil cost to compare with the plan.
4. **Auction:**
   - `/ah` window titles, including the page and bid variants;
   - full lore of a normal lot and a «Ставки» lot;
   - the purchase chat line.
5. **Alerts:**
   - what an Element item looks like;
   - whether Lite backpacks are shulker boxes named «Рюкзак»;
   - the shulker/element mode action-bar text.

The region intrusion message is the only format confirmed word for word by an official source.
