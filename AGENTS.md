# AGENTS.md — B2XY

Инструкция для агентов, работающих с этим репозиторием. Прочитай полностью перед изменениями.

## Что это

Аддон **B2XY** для [Meteor Client](https://github.com/MeteorDevelopment/meteor-client) на **Minecraft 1.21.11 (Fabric)**. Собирается в `B2XY-0.1.0.jar`, ставится в папку `mods` рядом с `meteor-client`. Модули портированы из bephax (`/home/anvres/bephax_sources`), meteor-rejects и Trouser-Streak, плюс самописные. Весь UI модулей (названия настроек, описания) — **на русском**.

## Ключевые факты

- Minecraft **1.21.11**, Yarn `1.21.11+build.3`, Loom `1.14-SNAPSHOT`, Fabric Loader.
- Java 21. Пакет: `com.b2xy`.
- База для проверки API — запускаемая игра: Prism Launcher, инстанс `Fabulously Optimized`, MC-джар и моды:
  `~/.local/share/ElyPrismLauncher/instances/Fabulously Optimized/minecraft/mods/`
- Распакованный (named, Yarn) клиент 1.21.11 для `javap`: `/tmp/opencode/mcjar/` (может не пережить перезагрузку; пересоздай из `~/.local/share/ElyPrismLauncher/libraries/com/mojang/minecraft/1.21.11/minecraft-1.21.11-client.jar` + ремап Yarn, либо из кеша Loom `~/.gradle/caches/fabric-loom/`).

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
  fabric.mod.json    — depends: minecraft 1.21.11, meteor-client *
```

## Правила для новых модулей

1. Класс в `com.b2xy.modules`, конструктор: `super(B2XY.CATEGORY, "id-модуля", "Описание на русском.")`. **Только** `B2XY.CATEGORY` — никаких категорий Meteor/bephax.
2. Настройки: `SettingGroup sgGeneral = settings.getDefaultGroup()`; имена настроек кириллицей в kebab-case (`"прочность-меньше"`).
3. Зарегистрировать в `B2XY.onInitialize()`.
4. События тиков — `@EventHandler onTick(TickEvent.Pre)`, подписка автоматическая.

## Миксины — критические правила 1.21.11

Эта связка (Loom 1.14) **не генерирует refmap**: `remapJar` переписывает имена прямо в байткоде. Отсюда:

- **Проверяй класс-владелец в `@At(target=...)`**: `javap -c` показывает вызов без пути к классу, если owner совпадает с текущим классом (`this.getPitch()` → owner `LivingEntity`, а не `Entity`). Несовпадение owner = `Scanned 0 target(s)` = краш при загрузке класса. Прецедент: `LivingEntityMixin.b2xy$wrapGlidingPitch`.
- **Сверяй сигнатуры с рантайм-джаром** (`javap -p` по классам из `/tmp/opencode/mcjar`), не по памяти: API 1.21.11 сильно отличается от 1.21.4/1.21.5. Известные переезды: `getArmorItems()` → `getEquippedStack(EquipmentSlot)`; `Input.movementForward/Sideways` → `movementVector` (`Vec2f`, x=боковое, y=вперёд); `LivingEntity.jumpCooldown` → `jumpingCooldown`; `KeyBinding.translationKey` → `getId()`; `InGameHud.renderOverlay` теперь `(DrawContext, Identifier, float)`; зачарования — через реестр (`RegistryKeys.ENCHANTMENT`, `Enchantments.MENDING` — это `RegistryKey<Enchantment>`).
- Вспомогательные методы в миксинах помечать `@Unique` (конфликт с bephax-аддоном, если тот включён).
- **`@Shadow` не ищет члены в суперклассах**: поле/метод должны быть объявлены именно в target-классе миксина, иначе `InvalidMixinException ... was not located` и краш при загрузке (прецедент: `client` из `ClientCommonNetworkHandler` при миксине на `ClientPlayNetworkHandler` — заменяется на `MinecraftClient.getInstance()`).
- После сборки проверять ремап: `javap -v` по классу из готового джара должен показывать `class_XXXX;method_XXXXX` (intermediary), а не Yarn-имена.
- `defaultRequire: 1` в `b2xy.mixins.json` — любая несработавшая инъекция валит игру, поэтому всё проверяется до выкладки.

## Сборка и выкладка

```bash
cd /home/anvres/B2XY
./gradlew build            # warnings remapJar о "отсутствующих" полях = реальный баг, не игнорировать
cp build/libs/B2XY-0.1.0.jar "/home/anvres/.local/share/ElyPrismLauncher/instances/Fabulously Optimized/minecraft/mods/"
```

Runtime-проверка юзером: лог запуска должен содержать `Hello from B2XYMixin!` и `Initializing B2XY Addon` без `Mixin transformation ... failed`. Краш-лог пользователи кидают целиком (PineconeMC-формат) — причина всегда в конце (`Caused by`).

## Конвенции

- Комментарии и тексты в коде/настройках — на русском.
- Baritone и ViaFabricPlus — **compile-only** (`modCompileOnly`) зависимости: не объявлять в `fabric.mod.json`.
- Не трогать шаблонные `ExampleCommand`, `ExampleHud`, `B2XYMixin` без явной просьбы.
- Старые джары в mods-папке (`*.disabled`, `*.duplicate`) не удалять самовольно.
