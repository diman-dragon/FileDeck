# FileDeck by DataLaw — нативный файловый менеджер для Android

Версия **0.0.6**. Kotlin + Jetpack Compose (Material 3).

Обзор памяти и SD-карты, копирование / перенос / удаление, переименование, поиск, просмотр картинок, текстовый
редактор, сетка и список, сортировка, глубокая очистка (дубликаты, похожие фото, мусор, пустые папки), корзина с
восстановлением и **архиватор**: ZIP с паролем AES-256, 7z, TAR (gz / bz2 / xz), распаковка RAR.

## Сборка в GitHub Actions
`.github/workflows/android.yml` при каждом push / pull request:
1. гоняет модульные тесты (`testDebugUnitTest`);
2. собирает debug APK, release APK и AAB;
3. кладёт `release/filedeck-<версия>*.apk|aab` в артефакты запуска (Actions → запуск → Artifacts).

Тег `vX.Y.Z` дополнительно публикует релиз на GitHub с этими файлами.
APK подписан ключом из `signing/`, поэтому новые сборки ставятся поверх установленных без удаления (`docs/SIGNING.md`).

## Правило версий (для людей и для ИИ)
**Каждое исправление или изменение повышает версию** — патч +1 (0.0.6 → 0.0.7 → 0.0.8 …), без исключений и без «тихих» правок
под тем же номером. При каждой выдаче нового архива:
1. поднять `app.version` в `gradle.properties` (единственный источник; versionCode считается сам);
2. добавить в начало `CHANGELOG.md` раздел новой версии с описанием правок;
3. обновить номер в `README.md` (строка «Версия …»).
Подробнее — `docs/VERSIONING.md`.

## Локальная сборка
Нужны JDK 21 и Android SDK (platform 36). Минимальная версия Android — 11 (API 30). Путь к SDK — `ANDROID_HOME` или `local.properties` (`sdk.dir=...`).

```bash
./gradlew assembleDebug         # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest     # тесты ядра и архивов
./gradlew bundleRelease         # AAB
```
Версия — `app.version` в `gradle.properties`.

## Архивы
| Операция | Форматы |
|---|---|
| Создание | ZIP (в т. ч. пароль AES-256), 7z, TAR, TAR.GZ, TAR.BZ2, TAR.XZ; уровни «быстро / обычно / максимум» |
| Просмотр содержимого и выборочная распаковка | ZIP (с паролем), 7z (с паролем), TAR, TAR.GZ/BZ2/XZ, GZ, BZ2, XZ |
| Распаковка целиком | RAR (v4 и старше) |

Распаковка «в папку с именем архива» или «сюда»; существующие файлы не перезаписываются (` (1)`), записи с `..`
отбрасываются (Zip Slip), ссылки пропускаются, при ошибке недоделанный результат удаляется. Есть прогресс и отмена.
Архив открывается по нажатию на файл; создаётся из выделения: **Ещё → Добавить в архив…**

## Структура
```
app/src/main/java/dev/filedeck/app/
  Fs.kt  Archives.kt  Cleaner.kt  Trash.kt  Settings.kt  Format.kt  Models.kt   ядро на Kotlin
  CleanerService / DuplicateFinder / SimilarImageFinder / CleanerLogic                   очистка
  FsUtil, FileRules, FileOpener, MimeTypes, Str, FileDeckApp, CrashActivity               утилиты и системная часть
  Zip*/SevenZ*/Tar*/SingleSource, ArchiveSink/Source/Format…                             движок форматов (Java)
  ui/  AppViewModel (навигация и состояние) · UiHost (диалоги, прогресс, уведомления) · FileActions (действия над файлами)
       ArchiveFlows / ArchiveBrowser / TrashController / CleanerController / EditorController — контроллеры экранов
       MainScreen, CleanerUi, SettingsScreen, TrashScreen, ArchiveScreen, ImageScreen, EditorScreen, AppRoot — экраны
       Theme, Icons, Widgets, Dialogs — тема и общие элементы
```

## Доступ к файлам
Нужно «Доступ ко всем файлам» (`MANAGE_EXTERNAL_STORAGE`): при первом запуске приложение откроет системный экран.
Для Google Play нужно обоснование разрешения (`docs/PUBLISHING.md`).
