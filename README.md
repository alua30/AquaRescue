# AquaRescue

Android-приложение для интеллектуального спасательного жилета на ESP32.
Мониторинг глубины (GY-MS5837-30BA), GPS-координат (NEO-6M), сигнала SOS
и состояния модулей по локальной сети Wi-Fi.

**Стек:** Kotlin · Jetpack Compose · Material 3 · Navigation Compose · ViewModel · StateFlow ·
Coroutines · DataStore. Сеть — `HttpURLConnection` + `org.json` (без сторонних HTTP-библиотек).
minSdk 26 (Android 8.0), targetSdk 37.

## Структура

```
app/src/main/java/com/aquarescue/app/
├── AquaRescueApp.kt, AppContainer.kt   — Application и ручное внедрение зависимостей
├── MainActivity.kt                      — только тема + навигация
├── model/        — модели: телеметрия, состояния тревоги/связи, события, настройки
├── network/      — HTTP-клиент ESP32, парсер JSON по контракту, монитор Wi-Fi
├── data/         — репозитории: мониторинг (опрос/переподключение/SOS), настройки, история
│   ├── source/   — источники телеметрии: DeviceTelemetrySource (ESP32), DemoTelemetrySource
│   └── alarm/    — звуковое оповещение на телефоне
├── domain/       — бизнес-логика: AlarmEvaluator, TelemetryEventDetector, GeoMath
├── presentation/ — ViewModel'и экранов (StateFlow)
├── navigation/   — NavHost + нижняя панель
├── ui/           — тема Dark Marine, компоненты, экраны (home, map, history, settings)
└── util/         — форматирование
docs/ESP32_API.md                        — контракт API ESP32 ↔ приложение
firmware/aquarescue_esp32/*.ino          — эталонный скетч прошивки (шаблон, не проверен на железе)
tools/esp32_mock_server.py               — эмулятор ESP32 для проверки сети без оборудования
```

## Сборка

```
gradlew assembleDebug          # APK: app/build/outputs/apk/debug/app-debug.apk
gradlew testDebugUnitTest      # unit-тесты (парсер, правила тревоги, события)
```

## Запуск

1. Откройте папку проекта в Android Studio, дождитесь Gradle Sync.
2. Эмулятор: **Device Manager** → AVD `Pixel 7 API 35` → если образ не скачан, Android Studio
   предложит загрузить его (System Image *Android 15, x86_64*). Либо подключите телефон
   с включённой отладкой по USB.
3. Нажмите **Run ▶** (конфигурация `app`).
4. При первом запуске включён **демо-режим** — все экраны работают с тестовыми данными
   (помечены фиолетовой меткой «ДЕМО»). Сценарии переключаются в *Настройки → Режим работы*.

### Проверка режима ESP32 без оборудования

```
python tools/esp32_mock_server.py --port 8080
```
В приложении: *Настройки* → IP `10.0.2.2` (так эмулятор видит компьютер), порт `8080` →
«Сохранить» → «Проверить» → выключить «Демонстрационный режим».
Остановите сервер (Ctrl+C) — через 6 с приложение покажет CONNECTION_LOST и начнёт переподключение.
`--reject-sos` — проверить сценарий «SOS не подтверждён», `--button-sos-after 20` — нажатие кнопки на жилете.

### Подключение реального ESP32

1. Собрать схему (распиновка — в шапке `firmware/aquarescue_esp32/aquarescue_esp32.ino`).
2. Установить библиотеки: BlueRobotics MS5837, TinyGPSPlus, DFRobotDFPlayerMini, ArduinoJson 7.
3. Прошить ESP32 (плата *ESP32 Dev Module*), на microSD DFPlayer положить `0001.mp3`.
4. Подключить телефон к Wi-Fi `AquaRescue-01` (пароль в скетче).
5. В приложении: IP `192.168.4.1`, порт `80` → «Проверить» → выключить демо-режим.

## Языки

Русский (по умолчанию, `res/values`), казахский (`res/values-kk`), английский (`res/values-en`).
Переключение: *Настройки → Язык*. Используется per-app language API
(`AppCompatDelegate.setApplicationLocales`, `res/xml/locales_config.xml`) — язык меняется сразу,
без перезапуска приложения, и сохраняется (Android 13+ — системой, Android 8–12 — AppCompat).
На Android 13+ язык также можно выбрать в системных настройках приложения.

Все тексты интерфейса — только в `strings.xml`. Слои data/domain возвращают структуры
(`AppError`, `AlarmReason`, параметры событий), текст собирается в `ui/text/TextResolvers.kt`,
поэтому история событий тоже отображается на текущем языке.

## Карта

Google Maps SDK требует API-ключ, поэтому не используется. Вместо этого:
схема траектории (Canvas, север сверху, масштабная линейка) и кнопка «Открыть в картах»
(geo:-ссылка — любое приложение карт), копирование и отправка координат.
