# Skirmish

Клиентский Fabric-мод для Minecraft 1.21.11 (Java 21) под PvP-сервер HolyWorld. Только чтение событий, отрисовка и UI:
никаких автодействий и пакетов, кроме обычного сообщения в чат от `.share` (ClanShare).

## Версии

| Компонент | Версия |
|---|---|
| Minecraft | 1.21.11 |
| Маппинги | официальные Mojang (`loom.officialMojangMappings()`) |
| Fabric Loader | сборка на 0.19.5, в рантайме `>=0.17.3` |
| Fabric API | 0.141.6+1.21.11 |
| Loom | 1.17.21, плагин `net.fabricmc.fabric-loom-remap` (последний Loom, который запускается на Java 21; 1.18 требует JVM 25) |
| Gradle | 9.7.1 (wrapper) |

## Сборка

```
./gradlew build          # jar в build/libs, тесты JUnit 5
./gradlew genSources     # исходники Minecraft для навигации по API
```

## Модули

Меню: **Правый Shift** (меняется в «Управление → Skirmish») или `/skirmish`.
У каждого модуля в меню есть переключатель, настройки и флажок «Писать в debug.log».

| id | Что делает |
|---|---|
| `combat` | трекер боя (ядро, всегда включён): удары, здоровье/поглощение, тотемы, смерти, атрибуция килла |
| `waypoints` | метки в мире с дистанцией, стрелка к выбранной метке, список в меню (ядро) |
| `killcam` | повтор последних 10 секунд перед смертью |
| `clanshare` | шифрованные координаты для соклановцев: `.share [название]` |
| `gearinspector` | панель с бронёй, зачарованиями и прочностью игрока под прицелом |
| `anvilcalc` | порядок объединения книг на наковальне с минимальной стоимостью |
| `killcard` | PNG-карточка после килла |

## Файлы в игре

- `config/skirmish/config.json` — настройки всех модулей (сохраняется через 0,5 с после изменения).
- `config/skirmish/waypoints.json` — метки с привязкой к серверу и измерению.
- `config/skirmish/debug.log` (+ `debug.1.log`, `debug.2.log`) — отладочный лог: `время [модуль] сообщение`,
  ротация по 2 МБ. Запись буферизована и идёт из фонового потока раз в секунду.

## Команды (клиентские, на сервер не уходят)

```
/skirmish                          меню
/skirmish wp                       список меток (экран)
/skirmish wp list                  список меток в чат
/skirmish wp here [название]       метка на моей позиции
/skirmish wp add <x> <y> <z> <измерение> [название]   измерение: overworld, the_nether, "minecraft:the_end"
/skirmish wp select <id> | unselect | remove <id>     id — первые 8 символов из списка
```

## Устройство кода

```
dev.skirmish
├── SkirmishClient          точка входа, регистрация модулей, тик, отложенное открытие экранов
├── SkirmishKeys            все KeyMapping мода (включая бинды модулей)
├── module/                 Module, ModuleManager; module/<id>/ — код модулей
├── setting/                BoolSetting, NumberSetting, EnumSetting, StringSetting (пароль), ActionSetting
├── config/                 ConfigManager (Gson)
├── debug/                  DebugLog
├── combat/                 CombatLogic (без классов MC, покрыт тестами), CombatTracker, CombatListener
├── waypoint/               WaypointStore (тесты), WaypointManager, HUD
├── gui/                    меню и список меток
├── command/                /skirmish
└── mixin/                  ClientPacketListenerMixin — единственный хук боевых пакетов
```

### Правила для модулей

- Модуль трогает только свой пакет `dev.skirmish.module.<id>`, свой `skirmish-<id>.mixins.json`,
  свой языковой неймспейс `assets/skirmish_<id>/lang/` и свои тесты `src/test/java/dev/skirmish/module/<id>/`.
  Minecraft сливает lang-файлы всех неймспейсов в одну таблицу, поэтому ключи вида
  `skirmish.module.<id>.setting.<настройка>` кладутся в неймспейс модуля.
- События боя — только через `CombatTracker.get().addListener(...)`. Не ставить миксины на
  `handleDamageEvent`, `handleEntityEvent`, `handleAnimate`, `handleSetEntityData`, `handlePlayerCombatKill`.
- Бинды берутся из `SkirmishKeys`; новые бинды добавляются в ядро.
- Регистрация Fabric-событий, HUD-элементов и слушателей — в `onInitialize()`; слушатели сами проверяют `isEnabled()`.
- Диагностика — `log(...)` модуля (пишет в debug.log, если у модуля включён лог).

### Заметки по API 1.21.11 (Mojang-имена)

- `ResourceLocation` здесь называется `Identifier`; `ResourceKey.identifier()`.
- `GuiGraphics.drawString` принимает ARGB: цвет без альфы (`0xFFFFFF`) невидим, нужно `0xFFFFFFFF`.
  `graphics.pose()` — это JOML `Matrix3x2fStack` (`pushMatrix/translate/scale/rotate/popMatrix`).
- `GameProfile` — record: `profile.name()`, `profile.id()`.
- Предметы, зачарования, прочность — только data components (`DataComponents.*`).
- Fabric выполняет зарегистрированные клиентские команды до отправки на сервер, в том числе из
  `ClickEvent.RunCommand` в чате.
