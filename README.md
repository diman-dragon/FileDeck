# FileDeck by DataLaw — файловый менеджер для Android (Capacitor 8)

Текущая версия: **2.0.6** (см. `CHANGELOG.md`).

Полноценный файловый менеджер: обзор памяти и SD-карты, копирование / перенос / удаление, переименование,
zip-архивы, поиск, просмотр картинок, текстовый редактор, сетка и список, сортировка.
В шапке всегда есть переключатель «Память / SD-карта», справа — шестерёнка настроек.
Темы: тёмная (чистый чёрный), светлая и «как в системе».
Глубокая очистка (дубликаты, похожие фото, мусор, пустые папки), корзина с восстановлением и архиватор
(ZIP с паролем, 7z, TAR, RAR).
Интерфейс — обычные HTML/CSS/JS (Vite), работа с файлами — собственный нативный плагин на Java.

## Что нужно установить

| Инструмент | Версия | Зачем |
|---|---|---|
| Node.js | 22+ | сборка веб-части и Capacitor CLI |
| JDK | 21 | Gradle / Android Gradle Plugin |
| Android SDK | platform 36 + build-tools | сборка APK (минимальная версия приложения — Android 8.0) |
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

Готовый APK: `release/filedeck-2.0.6-debug.apk`.
Установка на телефон по USB (включите «Отладку по USB») — **поверх установленной версии**:

```bash
npm run install:device
```

Или одной командой собрать и запустить: `npm run run:device`.

## Версии и обновления

Версия хранится в одном месте — `package.json`. Перед каждым выпуском:

```bash
npm run bump          # 2.0.0 → 2.0.1 (или: bump minor / bump major / bump 1.4.0)
```

`versionCode` для Android считается автоматически, а APK подписывается ключом из `android/signing/`
(одинаковым на всех машинах), поэтому новая сборка устанавливается как **обновление**, без удаления.
Если на телефоне осталась версия 1.0.0 с другим ключом — удалите её один раз. Подробности — `docs/SIGNING.md`.
Проверить заранее: `npm run doctor` (подключите телефон по USB).

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
| `npm run doctor` | проверить окружение и подпись; если подключён телефон — обновится ли сборка поверх |
| `npm run keygen` | создать личный ключ для Google Play |
| `npm run test:native` | 81 проверка нативной логики на JVM |
| `npm run icons` | пересобрать иконки из `src/assets/d-mark.png` |
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
│  ├─ app.js                  точка входа: запуск приложения
│  ├─ app/                    приложение: состояние, навигация, экраны (views/), действия (actions/), события (events/)
│  ├─ settings/               экраны настроек: главное меню и по файлу на раздел (sections/)
│  ├─ cleaner/                глубокая очистка: сканирование, правила выбора, удаление, экраны (views/)
│  ├─ trash/                  корзина: экран, действия, окно прогресса
│  ├─ archive/                создание, просмотр и распаковка архивов
│  ├─ layers/                 стек «Назад», листы, диалоги, тосты
│  ├─ viewer/                 просмотр изображений и текстовый редактор
│  ├─ icons/                  набор иконок и фирменная папка
│  ├─ mock/                   имитация нативных плагинов для разработки в браузере
│  ├─ plugin.js               реестр нативных плагинов (FM, CL, TR, AR)
│  ├─ platform.js · file-url.js · listen.js
│  ├─ format.js · path.js · html.js · sort.js · debounce.js · file-kinds.js · archive-types.js
│  ├─ assets/d-mark.png       логотип DataLaw (маска для CSS)
│  └─ styles.css + styles/    оформление: порядок подключения частей задаёт каскад
├─ android/                   нативный проект (Gradle)
│  └─ app/src/main/
│     ├─ java/dev/filedeck/app/
│     │  ├─ MainActivity.java         регистрирует плагины
│     │  ├─ FileManagerPlugin.java    мост «файлы»: FileBrowser, FileOps, TextFiles, FileOpener, FileJson, MimeTypes
│     │  ├─ CleanerPlugin.java        мост «очистка»: Scanner, DuplicateFinder, SimilarImageFinder, FileKinds
│     │  ├─ TrashPlugin.java          корзина: TrashStore (хранилище)
│     │  ├─ ArchivePlugin.java        мост «архивы»: ArchiveCreator / Lister / Extractor, форматы, Sink/Source
│     │  └─ FsUtil · JobRunner · JsArrays · Cancelled   общие утилиты
│     ├─ AndroidManifest.xml          разрешения
│     ├─ (android/signing/)           ключ для сборок «для себя»
│     └─ res/xml/file_paths.xml       FileProvider (открыть / поделиться)
├─ scripts/                   gradle.mjs, adb-install.mjs, bump.mjs
├─ docs/                      публикация, политика конфиденциальности, тексты магазина
├─ branding/                  логотип DataLaw, иконка 512×512 для Play Store
├─ tools/native-tests/        проверки нативной логики на JVM
├─ CHANGELOG.md
├─ .vscode/                   задачи, настройки, рекомендации расширений
└─ .github/workflows/android.yml   сборка APK в GitHub Actions
```

## Как это устроено

Стандартный плагин `@capacitor/filesystem` работает только с папками приложения, поэтому для файлового
менеджера написан свой плагин `FileManager` (Java). Он оперирует обычными путями
(`/storage/emulated/0/...`) и умеет: `getRoots`, `list`, `search`, `size`, `mkdir`, `createFile`,
`rename`, `remove`, `copy`, `move`, `readText`, `writeText`, `open`, `share`,
`checkPermission`, `requestPermission`. Плагины `Cleaner` (scan, cancelScan), `Trash` (move, list, restore,
remove, empty, purge, cancel) и `Archive` (create, list, extract) вынесены отдельно. Тяжёлые операции идут в отдельном пуле потоков и не блокируют
интерфейс. Распаковка защищена от Zip Slip, копирование папки внутрь самой себя запрещено,
при совпадении имён добавляется « (1)» — существующие файлы не перезаписываются.

Чтобы добавить функцию: метод в `FileManagerPlugin.java` (логика — в соседнем классе) → вызов `FM.метод()` в
нужном модуле `src/app/` → заглушка в `src/mock/file-manager.js`, чтобы работало и в браузере.

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
