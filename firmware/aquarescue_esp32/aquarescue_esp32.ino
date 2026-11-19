/*
 * AquaRescue — эталонная прошивка ESP32 (ШАБЛОН, НЕ ПРОВЕРЕН НА ЖЕЛЕЗЕ).
 *
 * Реализует HTTP API из docs/ESP32_API.md (api_version = 1), с которым работает
 * Android-приложение AquaRescue. Аппаратная сборка ещё не выполнена — перед
 * использованием проверьте распиновку, питание и калибровку на реальной схеме.
 *
 * Плата: ESP32 Dev Module (ESP32-WROOM-32, USB-UART CH340).
 * Библиотеки (Arduino Library Manager):
 *   - BlueRobotics MS5837 Library   (GY-MS5837-30BA, I2C)
 *   - TinyGPSPlus                   (NEO-6M)
 *   - DFRobotDFPlayerMini           (DFPlayer Mini)
 *   - ArduinoJson (v7)
 *   WiFi.h и WebServer.h входят в ядро ESP32.
 *
 * Подключение (предлагаемое):
 *   MS5837  : SDA -> GPIO21, SCL -> GPIO22, VCC 3.3V
 *   NEO-6M  : TX -> GPIO16 (RX2), RX -> GPIO17 (TX2), VCC 3.3–5V
 *   DFPlayer: TX -> GPIO26, RX <- GPIO27 через резистор 1 кОм, SPK1/SPK2 -> динамик 8 Ом 2 Вт
 *   MAX9814 : OUT -> GPIO34 (ADC1), VDD 3.3V
 *   SOS     : кнопка между GPIO32 и GND (внутренняя подтяжка)
 *   Buzzer  : GPIO25 через NPN-транзистор (активный зуммер)
 *
 * MicroSD DFPlayer: 0001.mp3 — голосовое сообщение SOS.
 */

#include <WiFi.h>
#include <WebServer.h>
#include <Wire.h>
#include <MS5837.h>
#include <TinyGPSPlus.h>
#include <DFRobotDFPlayerMini.h>
#include <ArduinoJson.h>

// ---------------------------------------------------------------- Настройки
#define WIFI_MODE_AP 1                 // 1 — точка доступа (рекомендуется), 0 — клиент роутера
const char* AP_SSID = "AquaRescue-01";
const char* AP_PASS = "rescue2026";     // минимум 8 символов
const char* STA_SSID = "YourRouter";
const char* STA_PASS = "YourPassword";

const char* DEVICE_ID = "aquarescue-01";
const char* FIRMWARE_VERSION = "0.1.0";

const int PIN_GPS_RX = 16, PIN_GPS_TX = 17;
const int PIN_DF_RX = 26, PIN_DF_TX = 27;
const int PIN_MIC = 34;
const int PIN_SOS = 32;
const int PIN_BUZZER = 25;

const float FLUID_DENSITY = 1029.0f;    // морская вода; пресная — 997
const float DANGER_DEPTH_M = 1.5f;      // локальная оценка тревоги (поле "alarm")
const float WARNING_DEPTH_M = 0.5f;

// ---------------------------------------------------------------- Объекты
WebServer server(80);
MS5837 depthSensor;
TinyGPSPlus gps;
HardwareSerial gpsSerial(2);
HardwareSerial dfSerial(1);
DFRobotDFPlayerMini dfPlayer;

bool depthOk = false, dfOk = false;
float surfacePressure = 1013.25f;
float depthM = 0, pressureMbar = 0, waterTempC = 0;

bool sosActive = false;
const char* sosSource = "none";
unsigned long sosSince = 0;
bool muted = false;

int micMin = 4095, micMax = 0;
unsigned long lastSensorRead = 0, lastMicWindow = 0;
bool micOk = false;

// ---------------------------------------------------------------- SOS
void startSos(const char* source) {
  if (!sosActive) sosSince = millis();
  sosActive = true;
  sosSource = source;
  if (dfOk && !muted) dfPlayer.loop(1);  // голосовое сообщение по кругу
}

void stopSos() {
  sosActive = false;
  sosSource = "none";
  digitalWrite(PIN_BUZZER, LOW);
  if (dfOk) dfPlayer.stop();
}

void updateBuzzer() {
  // При активном SOS зуммер звучит прерывисто и НЕ отключается кнопкой «Звук».
  if (sosActive) digitalWrite(PIN_BUZZER, (millis() / 500) % 2);
  else digitalWrite(PIN_BUZZER, LOW);
}

void pollSosButton() {
  static unsigned long pressedAt = 0;
  if (digitalRead(PIN_SOS) == LOW) {
    if (pressedAt == 0) pressedAt = millis();
    if (millis() - pressedAt > 1000 && !sosActive) startSos("button");  // удержание 1 с
  } else {
    pressedAt = 0;
  }
}

// ---------------------------------------------------------------- Датчики
void readSensors() {
  if (depthOk) {
    depthSensor.read();
    pressureMbar = depthSensor.pressure();
    waterTempC = depthSensor.temperature();
    depthM = (pressureMbar - surfacePressure) * 100.0f / (FLUID_DENSITY * 9.80665f);
    if (depthM < 0) depthM = 0;
  }
}

void sampleMic() {
  int v = analogRead(PIN_MIC);
  micMin = min(micMin, v);
  micMax = max(micMax, v);
  if (millis() - lastMicWindow > 1000) {
    // Микрофон исправен, если сигнал «живой» (не прилип к 0 или 4095).
    micOk = (micMax - micMin) > 5 && micMax < 4095 && micMin > 0;
    micMin = 4095; micMax = 0;
    lastMicWindow = millis();
  }
}

const char* alarmState() {
  if (sosActive) return "DANGER";
  if (!depthOk) return "WARNING";
  if (depthM >= DANGER_DEPTH_M) return "DANGER";
  if (depthM >= WARNING_DEPTH_M) return "WARNING";
  return "NORMAL";
}

// ---------------------------------------------------------------- HTTP
void sendJson(int code, JsonDocument& doc) {
  String out;
  serializeJson(doc, out);
  server.send(code, "application/json; charset=utf-8", out);
}

void sendError(int code, const char* error) {
  JsonDocument doc;
  doc["ok"] = false;
  doc["error"] = error;
  sendJson(code, doc);
}

void handleStatus() {
  JsonDocument doc;
  doc["api_version"] = 1;
  doc["device_id"] = DEVICE_ID;
  doc["uptime_ms"] = millis();

  JsonObject d = doc["depth"].to<JsonObject>();
  d["valid"] = depthOk;
  d["depth_m"] = depthM;
  d["pressure_mbar"] = pressureMbar;
  d["temperature_c"] = waterTempC;

  bool hasFix = gps.location.isValid() && gps.location.age() < 3000;
  JsonObject g = doc["gps"].to<JsonObject>();
  g["valid"] = gps.location.isValid();
  g["fix"] = hasFix;
  g["lat"] = serialized(String(gps.location.lat(), 6));
  g["lon"] = serialized(String(gps.location.lng(), 6));
  g["satellites"] = gps.satellites.isValid() ? gps.satellites.value() : 0;
  g["hdop"] = gps.hdop.isValid() ? gps.hdop.hdop() : 99.9;
  g["age_ms"] = gps.location.isValid() ? gps.location.age() : 0;

  JsonObject s = doc["sos"].to<JsonObject>();
  s["active"] = sosActive;
  s["source"] = sosSource;
  s["since_ms"] = sosActive ? millis() - sosSince : 0;

  JsonObject a = doc["audio"].to<JsonObject>();
  a["muted"] = muted;
  a["dfplayer_ok"] = dfOk;
  a["buzzer_on"] = digitalRead(PIN_BUZZER) == HIGH;

  JsonObject sn = doc["sensors"].to<JsonObject>();
  sn["ms5837"] = depthOk ? "ok" : "error";
  sn["gps"] = gps.charsProcessed() < 10 ? "error" : (hasFix ? "ok" : "no_fix");
  sn["dfplayer"] = dfOk ? "ok" : "error";
  sn["mic"] = micOk ? "ok" : "error";
  sn["sos_button"] = "ok";

  JsonObject w = doc["wifi"].to<JsonObject>();
  w["mode"] = WIFI_MODE_AP ? "ap" : "sta";
  w["rssi"] = WIFI_MODE_AP ? 0 : WiFi.RSSI();
  w["ip"] = WIFI_MODE_AP ? WiFi.softAPIP().toString() : WiFi.localIP().toString();

  doc["alarm"] = alarmState();
  sendJson(200, doc);
}

void handleInfo() {
  JsonDocument doc;
  doc["api_version"] = 1;
  doc["device_name"] = "AquaRescue Vest";
  doc["device_id"] = DEVICE_ID;
  doc["firmware_version"] = FIRMWARE_VERSION;
  doc["chip"] = ESP.getChipModel();
  doc["mac"] = WiFi.macAddress();
  doc["ip"] = WIFI_MODE_AP ? WiFi.softAPIP().toString() : WiFi.localIP().toString();
  doc["wifi_mode"] = WIFI_MODE_AP ? "ap" : "sta";
  doc["uptime_ms"] = millis();
  JsonArray c = doc["components"].to<JsonArray>();
  c.add("MS5837-30BA"); c.add("NEO-6M"); c.add("DFPlayer Mini");
  c.add("MAX9814"); c.add("SOS button"); c.add("Active buzzer");
  sendJson(200, doc);
}

void handleSos() {
  JsonDocument req;
  if (deserializeJson(req, server.arg("plain"))) return sendError(400, "invalid_json");
  String action = req["action"] | "";
  if (action != "activate" && action != "cancel") return sendError(400, "invalid_action");

  if (action == "activate") startSos("app"); else stopSos();

  JsonDocument res;
  res["ok"] = true;
  res["request_id"] = req["request_id"] | "";  // обязательно вернуть тот же request_id
  res["sos_active"] = sosActive;
  sendJson(200, res);
}

void handleSound() {
  JsonDocument req;
  if (deserializeJson(req, server.arg("plain"))) return sendError(400, "invalid_json");
  muted = req["muted"] | false;
  if (dfOk) {
    if (muted) dfPlayer.pause();
    else if (sosActive) dfPlayer.loop(1);
  }
  JsonDocument res;
  res["ok"] = true;
  res["muted"] = muted;
  sendJson(200, res);
}

// ---------------------------------------------------------------- setup/loop
void setup() {
  Serial.begin(115200);
  pinMode(PIN_SOS, INPUT_PULLUP);
  pinMode(PIN_BUZZER, OUTPUT);
  digitalWrite(PIN_BUZZER, LOW);
  analogReadResolution(12);

  Wire.begin(21, 22);
  depthOk = depthSensor.init();
  if (depthOk) {
    depthSensor.setModel(MS5837::MS5837_30BA);
    depthSensor.setFluidDensity(FLUID_DENSITY);
    // Калибровка: давление на поверхности при включении (включать жилет на воздухе!)
    float sum = 0;
    for (int i = 0; i < 10; i++) { depthSensor.read(); sum += depthSensor.pressure(); delay(50); }
    surfacePressure = sum / 10;
  }
  Serial.printf("MS5837: %s, P0=%.1f mbar\n", depthOk ? "OK" : "ERROR", surfacePressure);

  gpsSerial.begin(9600, SERIAL_8N1, PIN_GPS_RX, PIN_GPS_TX);
  dfSerial.begin(9600, SERIAL_8N1, PIN_DF_RX, PIN_DF_TX);
  dfOk = dfPlayer.begin(dfSerial);
  if (dfOk) dfPlayer.volume(25);
  Serial.printf("DFPlayer: %s\n", dfOk ? "OK" : "ERROR");

#if WIFI_MODE_AP
  WiFi.mode(WIFI_AP);
  WiFi.softAP(AP_SSID, AP_PASS);
  Serial.printf("Wi-Fi AP %s, IP %s\n", AP_SSID, WiFi.softAPIP().toString().c_str());
#else
  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);
  WiFi.begin(STA_SSID, STA_PASS);
  while (WiFi.status() != WL_CONNECTED) { delay(500); Serial.print('.'); }
  Serial.printf("\nWi-Fi STA, IP %s\n", WiFi.localIP().toString().c_str());
#endif

  server.on("/api/status", HTTP_GET, handleStatus);
  server.on("/api/info", HTTP_GET, handleInfo);
  server.on("/api/sos", HTTP_POST, handleSos);
  server.on("/api/sound", HTTP_POST, handleSound);
  server.onNotFound([] { sendError(404, "not_found"); });
  server.begin();
}

void loop() {
  server.handleClient();
  while (gpsSerial.available()) gps.encode(gpsSerial.read());
  if (millis() - lastSensorRead > 200) { readSensors(); lastSensorRead = millis(); }
  sampleMic();
  pollSosButton();
  updateBuzzer();
}
