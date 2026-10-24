# Подпись и обновления «поверх»

Android считает APK обновлением, только если совпадают **три вещи**:

| Что | Как это устроено в проекте |
|---|---|
| `applicationId` | `dev.filedeck.app`, не меняется |
| подпись (сертификат) | один ключ в `signing/` — на всех ваших машинах и в GitHub Actions |
| `versionCode` | считается из `app.version` в `gradle.properties`: major·10000 + minor·100 + patch |

Все сборки подписываются одним ключом, поэтому новая версия ставится поверх прежней без удаления.

## Какие ключи есть

1. **Общий ключ** — `signing/filedeck-sideload.jks` (лежит в git, пароли в `signing/signing.properties`).
   Им подписываются debug- и release-сборки в CI и локально. Он публичный: подходит для установки «для себя»,
   но **не** для магазина.
2. **Личный ключ для Google Play** — создайте `keystore.properties` в корне (он в `.gitignore`), по образцу:

   ```
   storeFile=signing/datalaw-upload.jks
   storePassword=...
   keyAlias=datalaw
   keyPassword=...
   ```
   Если файл есть, release подписывается им. Ключ создаётся так:
   `keytool -genkeypair -v -keystore signing/datalaw-upload.jks -alias datalaw -keyalg RSA -keysize 4096 -validity 10000`.
   Сделайте **копию в надёжном месте**: потеряв ключ, вы не сможете обновлять приложение в магазине.

## Если «Приложение не установлено»

- Ранее поставлена версия, собранная другим ключом: удалите её один раз (`adb uninstall dev.filedeck.app`).
- Версия на телефоне новее (versionCode больше): Android запрещает понижение. Поднимите `app.version`.

## Правила

1. Не меняйте `applicationId` и не пересоздавайте `signing/filedeck-sideload.jks`.
2. Перед каждой сборкой для раздачи поднимайте `app.version` в `gradle.properties`.
