# FileDeck by DataLaw — файловый менеджер для Android (Capacitor 8)

Текущая версия: **1.0.1** (см. `CHANGELOG.md`).

Полноценный файловый менеджер: обзор памяти и SD-карты, копирование / перенос / удаление, переименование,
zip-архивы, поиск, просмотр картинок, текстовый редактор, сетка и список, сортировка.
В шапке всегда есть переключатель «Память / SD-карта», справа — шестерёнка настроек.
Темы: AMOLED и светло-коричневая.
Интерфейс — обычные HTML/CSS/JS (Vite), работа с файлами — собственный нативный плагин на Java.

## Что нужно установить

| Инструмент | Версия | Зачем |
|---|---|---|
| Node.js | 22+ | сборка веб-части и Capacitor CLI |
| JDK | 21 | Gradle / Android Gradle Plugin |
| Android SDK | platform 36 + build-tools | сборка APK |
| git | любая | клонирование и версионирование |
| VS Code | любая | редактор (рекомендованные расширения подтянутся сами) |

Android SDK проще всего получить установкой Android Studio один раз, затем работать только в VS Code.
Укажите путь к SDK одним из способов:

```bash
# Linux / macOS
export ANDROID_HOME="$HOME/Android/Sdk"
# Windows (PowerShell)
setx ANDROID_HOME "$env:LOCALAPPDATA\Android\Sdk"
```

либо создайте `android/local.properties` со строкой `sdk.dir=/путь/к/Android/Sdk`.

## Быстрый старт

```bash
git init && git add -A && git commit -m "FileDeck: initial"   # если архив распакован без git
npm install
npm run apk
```

Готовый APK: `release/filedeck-1.0.1-debug.apk`.
Установка на телефон по USB (включите «Отладку по USB») — **поверх установленной версии**:

```bash
npm run install:device
```

Или одной командой собрать и запустить: `npm run run:device`.

## Версии и обновления

Версия хранится в одном месте — `package.json`. Перед каждым выпуском:

```bash
npm run bump          # 1.0.1 → 1.0.2 (или: bump minor / bump major / bump 1.4.0)
```

`versionCode` для Android считается автоматически, а APK подписывается ключом из `android/signing/`
(одинаковым на всех машинах), поэтому новая сборка устанавливается как **обновление**, без удаления.
Если на телефоне осталась версия 1.0.0 с другим ключом — удалите её один раз. Подробности — `docs/PUBLISHING.md`.

## Команды

| Команда | Что делает |
|---|---|
| `npm run dev` | веб-превью в браузере на моковой файловой системе — быстрая разработка интерфейса |
| `npm run build` | сборка веб-части в `dist/` |
| `npm run sync` | сборка + `cap sync android` (копирует веб в Android-проект) |
| `npm run apk` | sync + debug APK → `release/filedeck-<версия>-debug.apk` |
| `npm run apk:release` | release APK → `release/filedeck-<версия>.apk` |
| `npm run aab` | AAB для Google Play → `release/filedeck-<версия>.aab` |
| `npm run release` | и APK, и AAB |
| `npm run bump` | поднять версию |
| `npm run install:device` | `adb install -r` для debug APK |
| `npm run run:device` | sync + запуск на подключённом устройстве |
| `npm run open:studio` | открыть `android/` в Android Studio |

В VS Code все они есть в **Terminal → Run Task…** (`Ctrl+Shift+B` — сборка debug APK).

## Структура проекта

```
filedeck/
├─ index.html                 точка входа веб-части
├─ capacitor.config.json      appId, имя, webDir
├─ vite.config.js
├─ src/
│  ├─ main.js                 старт, подключение шрифтов и стилей
│  ├─ app.js                  состояние, навигация, выделение, действия
│  ├─ plugin.js               обёртка над нативным плагином FileManager
│  ├─ mock-fs.js              имитация ФС для разработки в браузере
│  ├─ layers.js               листы, диалоги, тосты, стек «Назад»
│  ├─ viewer.js               просмотр изображений и текстовый редактор
│  ├─ settings.js             экран настроек (шестерёнка)
│  ├─ assets/d-mark.png       логотип DataLaw (маска для CSS)
│  ├─ icons.js · utils.js
│  └─ styles.css              оформление (темы, адаптивность)
├─ android/                   нативный проект (Gradle)
│  └─ app/src/main/
│     ├─ java/dev/filedeck/app/
│     │  ├─ MainActivity.java         регистрирует плагин
│     │  └─ FileManagerPlugin.java    вся работа с файлами
│     ├─ AndroidManifest.xml          разрешения
│     ├─ (android/signing/)           ключ для сборок «для себя»
│     └─ res/xml/file_paths.xml       FileProvider (открыть / поделиться)
├─ scripts/                   gradle.mjs, adb-install.mjs, bump.mjs
├─ docs/                      публикация, политика конфиденциальности, тексты магазина
├─ branding/                  логотип DataLaw, иконка 512×512
├─ CHANGELOG.md
├─ .vscode/                   задачи, настройки, рекомендации расширений
└─ .github/workflows/android.yml   сборка APK в GitHub Actions
```

## Как это устроено

Стандартный плагин `@capacitor/filesystem` работает только с папками приложения, поэтому для файлового
менеджера написан свой плагин `FileManager` (Java). Он оперирует обычными путями
(`/storage/emulated/0/...`) и умеет: `getRoots`, `list`, `search`, `size`, `mkdir`, `createFile`,
`rename`, `remove`, `copy`, `move`, `zip`, `unzip`, `readText`, `writeText`, `open`, `share`,
`checkPermission`, `requestPermission`. Тяжёлые операции идут в отдельном пуле потоков и не блокируют
интерфейс. Распаковка защищена от Zip Slip, копирование папки внутрь самой себя запрещено,
при совпадении имён добавляется « (1)» — существующие файлы не перезаписываются.

Чтобы добавить функцию: метод в `FileManagerPlugin.java` → вызов `FM.метод()` в `src/app.js` →
заглушка в `src/mock-fs.js`, чтобы работало и в браузере.

## Доступ к файлам

На Android 11+ приложению нужно разрешение «Доступ ко всем файлам» (`MANAGE_EXTERNAL_STORAGE`).
При первом запуске FileDeck покажет экран с кнопкой — она открывает системные настройки, где
надо включить переключатель. На Android 10 и ниже используется обычный запрос доступа к памяти.

> Для публикации в Google Play это разрешение требует отдельного обоснования (категория
> «файловый менеджер» его допускает). Для личной установки APK ограничений нет.

## Публикация

Ключ для Google Play, чек-лист, тексты магазина и политика конфиденциальности — в папке `docs/`
(`PUBLISHING.md`, `STORE_LISTING.md`, `PRIVACY.md`).

## Сборка в облаке

`.github/workflows/android.yml` при каждом push собирает APK и AAB и кладёт их в артефакты
запуска (Actions → нужный запуск → Artifacts).

## Если что-то не собирается

- `SDK location not found` — не задан `ANDROID_HOME` / `android/local.properties`.
- `Unsupported class file major version` — Gradle запущен не на JDK 21 (`java -version`, `JAVA_HOME`).
- `adb: no devices` — включите отладку по USB и подтвердите ключ на телефоне (`adb devices`).
- Белый экран после правок интерфейса — забыли `npm run sync` (или используйте `npm run apk`).
- Список файлов пуст / «Нет доступа» — не выдано разрешение на доступ ко всем файлам.
- Первая сборка долгая: Gradle скачивает зависимости, дальше быстрее.

## Идеи для развития

Корзина с восстановлением, закладки папок, прогресс длинных операций через `notifyListeners`,
миниатюры видео, поддержка rar/7z, анализатор занятого места, SAF-режим для Google Play.
