# B2XY

Аддон [Meteor Client](https://github.com/MeteorDevelopment/meteor-client) для анархических
серверов `2b2t.org.ru` и `xyeta.online`. A Meteor Client addon for the anarchy servers
`2b2t.org.ru` and `xyeta.online`.

Все модули попадают в отдельную категорию **B2XY** в списке модулей Meteor, названия
настроек и описания — на русском языке.

| | |
|---|---|
| Minecraft | `1.21.11` |
| Yarn | `1.21.11+build.3` |
| Fabric Loader | `0.18.2` |
| Meteor Client | `1.21.11-SNAPSHOT` |
| Java | `21` |
| Baritone | `1.21.11-SNAPSHOT` |

## Поддерживаемые версии Minecraft

| Minecraft | Ветка | Статус |
|---|---|---|
| 1.21.11 | `main` | основная |
| 1.21.10 | `1.21.10` | порт в работе |
| 1.21.8 | `1.21.8` | собран и проверен |
| 1.21.5 | `1.21.5` | порт в работе |
| 1.21.4 | `1.21.4` | порт в работе |

26.2 не поддерживается: для всей ветки 26.x не публикуются ни маппинги Yarn, ни
Proguard-маппинги Mojang — в `version.json` у 26.1 и 26.2 нет ключей
`client_mappings` и `server_mappings`, потому что сама игра больше не обфусцируется.
Проект написан на Yarn, а переход на официальные имена означал бы переписывание всех
модулей и всех 15 миксинов, причём проверить результат без запуска игры на 26.2
нечем.

Версии разведены по веткам: у каждой свой `gradle/libs.versions.toml` со своей версией
Minecraft, Yarn и Meteor, а имя артефакта дополнено версией Minecraft
(`B2XY-1.21.8-0.1.1.jar`), чтобы сборки разных версий не совпадали именами.

Релиз сразу со всеми версиями собирает `.github/workflows/release-all.yml`: матрица
собирает каждую ветку, последний шаг публикует **один** релиз со всеми джарами и
отказывается публиковать неполный набор.

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
