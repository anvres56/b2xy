# B2XY

Аддон [Meteor Client](https://github.com/MeteorDevelopment/meteor-client) для анархических
серверов `2b2t.org.ru` и `xyeta.online`. A Meteor Client addon for the anarchy servers
`2b2t.org.ru` and `xyeta.online`.

Все модули попадают в отдельную категорию **B2XY** в списке модулей Meteor, названия
настроек и описания — на русском языке.

| | |
|---|---|
| Minecraft | `1.21.5` |
| Yarn | `1.21.5+build.1` |
| Fabric Loader | `0.18.2` |
| Meteor Client | `1.21.11-SNAPSHOT` |
| Java | `21` |
| Baritone | `1.21.11-SNAPSHOT` |

## Сборка / Build

Нужен JDK 21:

```bash
./gradlew build
```

Готовый аддон появляется в `build/libs/B2XY-0.1.0.jar`. Его нужно положить в папку
`mods` рядом с Meteor Client и запустить игру.

При каждом пуше в `main` GitHub Actions собирает снапшот и выкладывает его как
prerelease с тегом `snapshot` (`Assets` в релизе), так что свежую сборку можно не
собирать локально.

## Устройство проекта

```text
src/main/java/com/b2xy/
  B2XY.java                 входная точка: категория, регистрация модулей, команды, HUD
  modules/                  модули аддона
  mixin/                    миксины
  mixin/accessor/           accessor-миксины (доступ к приватным полям)
  util/                     утилиты (ротации, Grim-помощники, печать по схеме)
  commands/  hud/           команды и HUD-элементы
src/main/resources/
  b2xy.mixins.json          список миксинов (новый миксин обязан быть здесь)
  b2xy.accesswidener        доступ к приватным полям Minecraft
  fabric.mod.json           метаданные аддона
  assets/b2xy/icon.png      иконка
```

Правила для новых модулей: класс в `com.b2xy.modules`, конструктор
`super(B2XY.CATEGORY, "id-модуля", "Описание.")`, настройки в
`settings.getDefaultGroup()` с кириллическими именами в kebab-case, регистрация в
`B2XY.onInitialize()`.

## Модули

`ActivatedSpawnerDetector`, `AutoTnt`, `AutoWither`, `AutoXp`, `BepMine`, `BetterF5`,
`CrystalAuraTurbo`, `ElytraBounce`, `ElytraRecast`, `ElytraSwap`, `GrimAirPlace`,
`GrimGlide`, `GrimVelocity`, `GuiMove`, `HoleSnap`, `InvFix2b2t`, `LitematicaPrinter`,
`NameTags`, `NewChunks`, `NoHurtCam`, `NoJumpDelay`, `NoWeb`,
`Pitch40`, `Replenish`, `RocketBoost`, `VillagerRoller`.

## Происхождение кода

Это аддон, а не клиент: используется только публичный API Meteor Client, ничего из
Meteor не патчится. При этом часть модулей — порты идей и реализаций из других
клиентов (`BepHax`, `CatLean`, `meteor-villager-roller`, `Trouser-Streak`,
`HighwayBuilder`); в javadoc каждого такого модуля указано, откуда взята логика.
Никакие чужие джары в репозитории не лежат и в сборку не входят.

## Лицензия / License

Файл `LICENSE` — CC0 1.0 Universal, он пришёл из стартового шаблона Meteor
(`MeteorClient` addon template) и относится к коду шаблона и коду, написанному для
этого аддона. Если частично портированный код других клиентов попадает под
ограничения их авторов, лицензия на него не распространяется.
