#!/usr/bin/env python3
"""
Эмулятор ESP32 AquaRescue для проверки сетевого слоя приложения БЕЗ оборудования.

ВНИМАНИЕ: это не реальное устройство. Сервер реализует контракт docs/ESP32_API.md
и генерирует условные значения, чтобы проверить опрос, потерю связи, подтверждение SOS.
В приложении данные от него отображаются как данные устройства (режим ESP32),
поэтому используйте его только для тестирования.

Запуск (нужен только Python 3.8+, без сторонних библиотек):
    python tools/esp32_mock_server.py --port 8080

Адрес в приложении:
    * Android-эмулятор:     10.0.2.2, порт 8080
    * телефон в той же сети: IP компьютера, порт 8080

Параметры:
    --scenario auto|surface|dive   поведение глубины (по умолчанию auto)
    --reject-sos                   отвечать на /api/sos с ok=false (проверка ошибки SOS)
    --button-sos-after N           через N секунд «нажать» кнопку SOS на жилете
"""
import argparse
import json
import math
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

START = time.time()
STATE = {
    "sos_active": False,
    "sos_source": "none",
    "sos_since": 0.0,
    "muted": False,
    "last_fix": None,  # (lat, lon, t)
}
ARGS = None


def depth_at(t: float) -> float:
    if ARGS.scenario == "surface":
        return 0.05 + 0.03 * math.sin(t)
    if ARGS.scenario == "dive":
        return 1.0 + 0.8 * math.sin(t / 10)
    c = t % 90
    if c < 30:
        return 0.05
    if c < 50:
        return (c - 30) / 20 * 1.8
    if c < 60:
        return 1.8
    if c < 75:
        return 1.8 * (1 - (c - 60) / 15)
    return 0.05


def status() -> dict:
    now = time.time()
    t = now - START
    depth = max(0.0, depth_at(t))
    fix = depth < 0.2
    lat = 43.640120 + t * 0.000002
    lon = 51.120450 + t * 0.000003
    if fix:
        STATE["last_fix"] = (lat, lon, now)

    if ARGS.button_sos_after and t > ARGS.button_sos_after and STATE["sos_source"] == "none":
        STATE.update(sos_active=True, sos_source="button", sos_since=now)

    last = STATE["last_fix"]
    return {
        "api_version": 1,
        "device_id": "mock-esp32",
        "uptime_ms": int(t * 1000),
        "depth": {
            "valid": True,
            "depth_m": round(depth, 2),
            "pressure_mbar": round(1013.25 + depth * 98.1, 1),
            "temperature_c": 18.2,
        },
        "gps": {
            "valid": last is not None,
            "fix": fix,
            "lat": round(last[0], 6) if last else 0,
            "lon": round(last[1], 6) if last else 0,
            "satellites": 8 if fix else 0,
            "hdop": 1.1 if fix else 99.9,
            "age_ms": int((now - last[2]) * 1000) if last else 0,
        },
        "sos": {
            "active": STATE["sos_active"],
            "source": STATE["sos_source"] if STATE["sos_active"] else "none",
            "since_ms": int((now - STATE["sos_since"]) * 1000) if STATE["sos_active"] else 0,
        },
        "audio": {"muted": STATE["muted"], "dfplayer_ok": True, "buzzer_on": STATE["sos_active"]},
        "sensors": {
            "ms5837": "ok",
            "gps": "ok" if fix else "no_fix",
            "dfplayer": "ok",
            "mic": "ok",
            "sos_button": "ok",
        },
        "wifi": {"mode": "sta", "rssi": -61, "ip": "mock"},
    }


INFO = {
    "api_version": 1,
    "device_name": "AquaRescue Mock ESP32",
    "device_id": "mock-esp32",
    "firmware_version": "mock-0.1",
    "chip": "emulated",
    "mac": "00:00:00:00:00:00",
    "ip": "mock",
    "wifi_mode": "sta",
    "components": ["MS5837-30BA", "NEO-6M", "DFPlayer Mini", "MAX9814", "SOS button", "Active buzzer"],
}


class Handler(BaseHTTPRequestHandler):
    def _send(self, code: int, body: dict):
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _json_body(self):
        length = int(self.headers.get("Content-Length", 0))
        try:
            return json.loads(self.rfile.read(length) or b"{}")
        except json.JSONDecodeError:
            return None

    def do_GET(self):
        if self.path == "/api/status":
            self._send(200, status())
        elif self.path == "/api/info":
            self._send(200, dict(INFO, uptime_ms=int((time.time() - START) * 1000)))
        else:
            self._send(404, {"ok": False, "error": "not_found"})

    def do_POST(self):
        body = self._json_body()
        if body is None:
            return self._send(400, {"ok": False, "error": "invalid_json"})
        if self.path == "/api/sos":
            action = body.get("action")
            if action not in ("activate", "cancel"):
                return self._send(400, {"ok": False, "error": "invalid_action"})
            if ARGS.reject_sos:
                return self._send(200, {"ok": False, "request_id": body.get("request_id")})
            active = action == "activate"
            STATE.update(sos_active=active, sos_source="app" if active else "none", sos_since=time.time())
            return self._send(200, {"ok": True, "request_id": body.get("request_id"), "sos_active": active})
        if self.path == "/api/sound":
            STATE["muted"] = bool(body.get("muted"))
            return self._send(200, {"ok": True, "muted": STATE["muted"]})
        self._send(404, {"ok": False, "error": "not_found"})

    def log_message(self, fmt, *args):
        print("[mock-esp32] " + fmt % args)


def main():
    global ARGS
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--host", default="0.0.0.0")
    p.add_argument("--port", type=int, default=8080)
    p.add_argument("--scenario", choices=["auto", "surface", "dive"], default="auto")
    p.add_argument("--reject-sos", action="store_true")
    p.add_argument("--button-sos-after", type=float, default=0)
    ARGS = p.parse_args()
    print(f"Эмулятор ESP32 (НЕ реальное устройство) на http://{ARGS.host}:{ARGS.port} — Ctrl+C для остановки")
    ThreadingHTTPServer((ARGS.host, ARGS.port), Handler).serve_forever()


if __name__ == "__main__":
    main()
