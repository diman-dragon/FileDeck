# FileDeck — файловый менеджер для Android (Capacitor)

Полноценный файловый менеджер: обзор памяти и SD-карты, копирование / перенос / удаление, переименование,
zip-архивы, поиск, просмотр картинок, текстовый редактор, сетка и список, сортировка, тёмная и светлая темы.
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

Готовый APK: `android/app/build/outputs/apk/debug/app-debug.apk`.
Установка на телефон по USB (включите «Отладку по USB»):

```bash
npm run install:device
```

Или одной командой собрать и запустить: `npm run run:device`.

## Команды

| Команда | Что делает |
|---|---|
| `npm run dev` | веб-превью в браузере на моковой файловой системе — быстрая разработка интерфейса |
| `npm run build` | сборка веб-части в `dist/` |
| `npm run sync` | сборка + `cap sync android` (копирует веб в Android-проект) |
| `npm run apk` | sync + debug APK |
| `npm run apk:release` | sync + release APK (подписанный, если есть `android/keystore.properties`) |
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
│  ├─ icons.js · utils.js
│  └─ styles.css              оформление (темы, адаптивность)
├─ android/                   нативный проект (Gradle)
│  └─ app/src/main/
│     ├─ java/dev/filedeck/app/
│     │  ├─ MainActivity.java         регистрирует плагин
│     │  └─ FileManagerPlugin.java    вся работа с файлами
│     ├─ AndroidManifest.xml          разрешения
│     └─ res/xml/file_paths.xml       FileProvider (открыть / поделиться)
├─ scripts/                   gradle.mjs, adb-install.mjs (кроссплатформенные)
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

## Релизная подпись

```bash
keytool -genkey -v -keystore filedeck.jks -keyalg RSA -keysize 2048 -validity 10000 -alias filedeck
cp android/keystore.properties.example android/keystore.properties   # заполните пароли
npm run apk:release
```

APK: `android/app/build/outputs/apk/release/app-release.apk`. Ключ и `keystore.properties`
уже в `.gitignore` — не коммитьте их.

## Сборка в облаке

`.github/workflows/android.yml` собирает debug APK при каждом push в GitHub и кладёт его в артефакты
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
