# Публикация FileDeck

## 1. Обновления поверх установленной версии

Android ставит APK как **обновление**, если одновременно совпадают три вещи:

1. `applicationId` — `dev.filedeck.app` (не менять после первой публикации!);
2. подпись — один и тот же ключ;
3. `versionCode` новой версии больше прежней.

В проекте это сделано так:

- версия живёт только в `package.json`, `versionCode` считается автоматически (1.0.1 → 10001);
- `npm run bump` поднимает патч-версию (`bump minor`, `bump major`, `bump 1.4.0` — тоже можно);
- debug- и CI-сборки подписываются ключом из репозитория `android/signing/` — на любой машине
  подпись одинаковая, поэтому `npm run install:device` (`adb install -r`) и ручная установка APK
  обновляют приложение без потери настроек;
- если на телефоне стоит 1.0.0, собранная с другим (автоматическим debug) ключом, её нужно удалить
  **один раз**. Дальше всё обновляется поверх.

Порядок выпуска версии:

```bash
npm run bump            # 1.0.1 → 1.0.2
# допишите CHANGELOG.md
npm run release         # release/filedeck-1.0.2.apk и release/filedeck-1.0.2.aab
git add -A && git commit -m "Release 1.0.2" && git tag v1.0.2
```

## 2. Ключ для Google Play

Ключ из `android/signing/` публичный (он в git) — им нельзя подписывать релиз для магазина.

```bash
keytool -genkeypair -v -keystore android/signing/datalaw-upload.jks -storetype PKCS12 \
  -keyalg RSA -keysize 2048 -validity 36500 -alias datalaw
cp android/keystore.properties.example android/keystore.properties   # впишите пути и пароли
npm run release
```

`keystore.properties` и `*.jks` игнорируются git — **храните копию ключа и паролей в надёжном месте**.
В Play Console включите Play App Signing и загрузите `.aab` как ключ загрузки (upload key).
Если приложение уже раздавалось APK с ключом из репозитория, пользователи с такой установкой
не смогут обновиться на сборку из Play (другая подпись) — им нужно будет переустановить один раз.

## 3. Чек-лист перед отправкой

- [ ] Решено окончательное `applicationId` (сейчас `dev.filedeck.app`). Менять нужно **до** первой
      публикации: `namespace`/`applicationId` в `android/app/build.gradle`, пакет и папка
      `android/app/src/main/java/...`, `appId` в `capacitor.config.json`, `strings.xml`.
- [ ] Свой ключ загрузки создан, `keystore.properties` заполнен.
- [ ] `npm run bump`, запись в `CHANGELOG.md`.
- [ ] `npm run release` → `release/filedeck-X.Y.Z.aab`.
- [ ] Политика конфиденциальности размещена по публичному URL (текст: `docs/PRIVACY.md`).
- [ ] Заполнены «Безопасность данных» и декларация доступа ко всем файлам (`docs/STORE_LISTING.md`).
- [ ] Скриншоты (телефон, 16:9 или 9:16), значок 512×512 (`branding/icon-512.png`), графика 1024×500.
- [ ] Тест на реальных устройствах: Android 8, 11, 14+; с SD-картой и без.

## 4. Стек

Capacitor 8 (`@capacitor/core`, `@capacitor/android`, `@capacitor/app` 8.x), Vite 8, Node 22+,
JDK 21, Gradle 8.14, Android Gradle Plugin 8.13, compileSdk/targetSdk 36, minSdk 24.
Обновление Capacitor: `npm i @capacitor/core@latest @capacitor/android@latest @capacitor/app@latest`
и `npx cap migrate`.
