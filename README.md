# FileDeck by DataLaw — нативный файловый менеджер для Android

Версия **0.0.1**. Kotlin + Jetpack Compose (Material 3). Без WebView, Node.js и Capacitor.

Обзор памяти и SD-карты, копирование / перенос / удаление, переименование, поиск, просмотр картинок, текстовый
редактор, сетка и список, сортировка, глубокая очистка (дубликаты, похожие фото, мусор, пустые папки), корзина с
восстановлением и **архиватор**: ZIP с паролем AES-256, 7z, TAR (gz / bz2 / xz), распаковка RAR.

## Сборка в GitHub Actions
`.github/workflows/android.yml` при каждом push / pull request:
1. гоняет модульные тесты (`testDebugUnitTest`);
2. собирает debug APK, release APK и AAB;
3. кладёт `release/filedeck-<версия>*.apk|aab` в артефакты запуска (Actions → запуск → Artifacts).

Тег `vX.Y.Z` дополнительно публикует релиз на GitHub с этими файлами.
APK подписан ключом из `signing/` — тем же, что у версий 2.x, поэтому ставится **поверх** без удаления (`docs/SIGNING.md`).

## Локальная сборка
Нужны JDK 21 и Android SDK (platform 36). Путь к SDK — `ANDROID_HOME` или `local.properties` (`sdk.dir=...`).

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
  Zip*/SevenZ*/Tar*/SingleSource, ArchiveSink/Source/Format, FsUtil, FileOpener…  движок форматов (Java)
  ui/  AppViewModel, MainScreen, CleanerUi, Screens (настройки, корзина, архив, просмотр, редактор), Components, Controllers
```

## Доступ к файлам
На Android 11+ нужно «Доступ ко всем файлам» (`MANAGE_EXTERNAL_STORAGE`): при первом запуске приложение откроет
системный экран. На Android 10 и ниже — обычный запрос доступа к памяти. Для Google Play нужно обоснование разрешения
(`docs/PUBLISHING.md`).
