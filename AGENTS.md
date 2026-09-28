# AGENTS.md — B2XY

Инструкция для агентов, работающих с этим репозиторием. Прочитай полностью перед изменениями.

## Что это

Аддон **B2XY** для [Meteor Client](https://github.com/MeteorDevelopment/meteor-client) на **Minecraft 1.21.10 (Fabric)**. Собирается в `B2XY-mc1.21.10-<версия>.jar` (имя задаёт `archives_base_name`), ставится в папку `mods` рядом с `meteor-client`. Модули портированы из bephax (`/home/anvres/bephax_sources`), meteor-rejects и Trouser-Streak, плюс самописные. Весь UI модулей (названия настроек, описания) — **на русском**.

## Ключевые факты

- Minecraft **1.21.10**, Yarn `1.21.10+build.3`, Loom `1.14-SNAPSHOT`, Fabric Loader.
  Ветка `1.21.10`: код портирован с 1.21.11, где API Minecraft в используемом
  аддоном подмножестве **совпадает** — расхождений в коде нет, но имена в
  комментариях и в `AGENTS.md` ещё называют 1.21.11.
- Java 21. Пакет: `com.b2xy`.
- База для проверки API — запускаемая игра: Prism Launcher, инстанс `Fabulously Optimized`, MC-джар и моды:
  `~/.local/share/ElyPrismLauncher/instances/Fabulously Optimized/minecraft/mods/`
- Распакованный (named, Yarn) клиент 1.21.10 для `javap` берётся из кеша Loom (он
  скачается при первой сборке):
  `MC=$(find ~/.gradle/caches/fabric-loom -path "*1.21.10*" -name "minecraft-merged-*.jar" | grep -v intermediary)`
  Маппинги 1.21.10: `~/.gradle/caches/fabric-loom/1.21.10/net.fabricmc.yarn.*/mappings.tiny`
  (формат: `c <official> <intermediary> <named>`, у членов класса первым идёт
  **пустой** столбец, поэтому `m`/`f` сдвинуты на 1).

## Структура

```
src/main/java/com/b2xy/
  B2XY.java          — входная точка (MeteorAddon): регистрация категории, модулей, команд, HUD
  modules/           — все модули (Module), StashMoverSelectionHandler — вспомогательный
  mixin/             — миксины (LivingEntityMixin, KeyBindingMixin, InputMixin, B2XYMixin)
  mixin/accessor/    — accessor-миксины (PlayerInventoryAccessor, FireworkRocketEntityAccessor)
  util/              — RotationUtils, ViaProtocolUtil, BounceSolver и пр.
  commands/, hud/    — ExampleCommand, ExampleHud (шаблонные, не порт)
src/main/resources/
  b2xy.mixins.json   — список миксинов; новый миксин ОБЯЗАТЕЛЬНО добавить сюда
  fabric.mod.json    — depends: minecraft 1.21.10, meteor-client *
```

## Правила для новых модулей

1. Класс в `com.b2xy.modules`, конструктор: `super(B2XY.CATEGORY, "id-модуля", "Описание на русском.")`. **Только** `B2XY.CATEGORY` — никаких категорий Meteor/bephax.
2. Настройки: `SettingGroup sgGeneral = settings.getDefaultGroup()`; имена настроек кириллицей в kebab-case (`"прочность-меньше"`).
3. Зарегистрировать в `B2XY.onInitialize()`.
4. События тиков — `@EventHandler onTick(TickEvent.Pre)`, подписка автоматическая.

## Миксины — критические правила (проверено на 1.21.10 и 1.21.11)

Эта связка (Loom 1.14) **не генерирует refmap**: `remapJar` переписывает имена прямо в байткоде. Отсюда:

- **Проверяй класс-владелец в `@At(target=...)`**: `javap -c` показывает вызов без пути к классу, если owner совпадает с текущим классом (`this.getPitch()` → owner `LivingEntity`, а не `Entity`). Несовпадение owner = `Scanned 0 target(s)` = краш при загрузке класса. Прецедент: `LivingEntityMixin.b2xy$wrapGlidingPitch`.
- **Сверяй сигнатуры с рантайм-джаром** (`javap -p` по `$MC`), не по памяти. API сильно менялся: 1.21.4/1.21.5 → 1.21.10 (`getArmorItems()` → `getEquippedStack(EquipmentSlot)`; `Input.movementForward/Sideways` → `movementVector` (`Vec2f`, x=боковое, y=вперёд); `LivingEntity.jumpCooldown` → `jumpingCooldown`; `KeyBinding.translationKey` → `getId()`; `InGameHud.renderOverlay` = `(DrawContext, Identifier, float)`; зачарования — через `RegistryKeys.ENCHANTMENT`, `Enchantments.MENDING` это `RegistryKey<Enchantment>`; `PickaxeItem`/`SwordItem` удалены, оружие — компонент `DataComponentTypes.WEAPON`; `SpawnerBlockEntity` → `MobSpawnerBlockEntity`, `nextSpawnData` → `spawnPotentials`).
  **Между 1.21.10 и 1.21.11 в используемом аддоном подмножестве расхождений нет.** Единственная известная разница — `MathHelper.sin/cos`: в 1.21.10 `(F)F`, в 1.21.11 `(D)F`; вызовы с `float` совместимы с обеими, менять не нужно.
- Вспомогательные методы в миксинах помечать `@Unique` (конфликт с bephax-аддоном, если тот включён).
- **`@Shadow` не ищет члены в суперклассах**: поле/метод должны быть объявлены именно в target-классе миксина, иначе `InvalidMixinException ... was not located` и краш при загрузке (прецедент: `client` из `ClientCommonNetworkHandler` при миксине на `ClientPlayNetworkHandler` — заменяется на `MinecraftClient.getInstance()`).
- После сборки проверять ремап: `javap -v` по классу из готового джара должен показывать `class_XXXX;method_XXXXX` (intermediary), а не Yarn-имена.
- `defaultRequire: 1` в `b2xy.mixins.json` — любая несработавшая инъекция валит игру, поэтому всё проверяется до выкладки.

## Сборка и выкладка

```bash
cd /home/anvres/B2XY
./gradlew build            # warnings remapJar о "отсутствующих" полях = реальный баг, не игнорировать
cp build/libs/B2XY-mc1.21.10-0.1.1.jar "/home/anvres/.local/share/ElyPrismLauncher/instances/Fabulously Optimized/minecraft/mods/"
```

Предупреждения Loom вида `Cannot remap onEntityCollision because it does not exists in
any of the targets [net/minecraft/class_2560]` (CobwebBlockMixin) — **ложные**: Yarn
не маппит override, объявленный в подклассе (у `CobwebBlock.onEntityCollision` в
маппингах нет, метод описан у `AbstractBlock` как `method_9548`). В байткоде цели
корректны, проверено `javap -v`.

Runtime-проверка юзером: лог запуска должен содержать `Hello from B2XYMixin!` и `Initializing B2XY Addon` без `Mixin transformation ... failed`. Краш-лог пользователи кидают целиком (PineconeMC-формат) — причина всегда в конце (`Caused by`).

## Конвенции

- Комментарии и тексты в коде/настройках — на русском.
- Baritone и ViaFabricPlus — **compile-only** (`modCompileOnly`) зависимости: не объявлять в `fabric.mod.json`.
- Не трогать шаблонные `ExampleCommand`, `ExampleHud`, `B2XYMixin` без явной просьбы.
- Старые джары в mods-папке (`*.disabled`, `*.duplicate`) не удалять самовольно.
