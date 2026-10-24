# Публикация

## Версии
- Версия — `app.version` в `gradle.properties`; `versionCode` считается автоматически.
- Выпуск: поднять `app.version`, записать изменения в `CHANGELOG.md`, создать тег `vX.Y.Z` — GitHub Actions соберёт
  APK и AAB и приложит их к релизу.

## Для Google Play
Общий ключ из `signing/` публичный — им нельзя подписывать релиз для магазина. Создайте личный ключ и
`keystore.properties` (см. `SIGNING.md`), затем `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`.

## Чек-лист
- [ ] Разрешение «Доступ ко всем файлам» (`MANAGE_EXTERNAL_STORAGE`) обосновано в форме Play Console
      (категория «файловый менеджер»).
- [ ] Личный ключ создан, резервная копия .jks и паролей сохранена.
- [ ] `app.version` поднята, запись в `CHANGELOG.md` есть.
- [ ] Политика конфиденциальности (`docs/PRIVACY.md`) опубликована по ссылке.
- [ ] Тексты магазина — `docs/STORE_LISTING.md`.

## Стек
Kotlin 2.2, Jetpack Compose (Material 3), AGP 8.13, Gradle 8.14, JDK 21, minSdk 30 (Android 11), target/compile SDK 36.
