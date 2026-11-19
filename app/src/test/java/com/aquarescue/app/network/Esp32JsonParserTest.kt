package com.aquarescue.app.network

import com.aquarescue.app.model.AlarmState
import com.aquarescue.app.model.DataSource
import com.aquarescue.app.model.SensorStatus
import com.aquarescue.app.model.SosSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Проверка разбора ответов по контракту docs/ESP32_API.md. */
class Esp32JsonParserTest {

    private val docExample = """
        {
          "api_version": 1,
          "device_id": "aquarescue-01",
          "uptime_ms": 734512,
          "depth": {"valid": true, "depth_m": 0.42, "pressure_mbar": 1054.3, "temperature_c": 18.6},
          "gps": {"valid": true, "fix": true, "lat": 43.640120, "lon": 51.120450, "satellites": 7, "hdop": 1.2, "age_ms": 850},
          "sos": {"active": false, "source": "none", "since_ms": 0},
          "audio": {"muted": false, "dfplayer_ok": true, "buzzer_on": false},
          "sensors": {"ms5837": "ok", "gps": "ok", "dfplayer": "ok", "mic": "ok", "sos_button": "ok"},
          "wifi": {"mode": "ap", "rssi": -58, "ip": "192.168.4.1"},
          "alarm": "NORMAL",
          "future_field": {"anything": 1}
        }
    """.trimIndent()

    @Test
    fun parsesDocumentationExample() {
        val s = Esp32JsonParser.parseStatus(docExample, receivedAt = 1000L)
        assertEquals(DataSource.DEVICE, s.source)
        assertEquals(1, s.apiVersion)
        assertEquals("aquarescue-01", s.deviceId)
        assertEquals(0.42, s.depth.depthM!!, 1e-9)
        assertEquals(1054.3, s.depth.pressureMbar!!, 1e-9)
        assertTrue(s.gps.fix)
        assertEquals(43.640120, s.gps.lat!!, 1e-9)
        assertEquals(7, s.gps.satellites)
        assertEquals(850L, s.gps.ageMs)
        assertFalse(s.sos.active)
        assertEquals(SosSource.NONE, s.sos.source)
        assertEquals(SensorStatus.OK, s.sensors["ms5837"])
        assertEquals(-58, s.wifi?.rssi)
        assertEquals(AlarmState.NORMAL, s.deviceAlarm)
        assertEquals(1000L, s.receivedAt)
    }

    @Test
    fun keepsLastKnownCoordinatesWithoutFix() {
        val json = docExample.replace("\"fix\": true", "\"fix\": false")
        val s = Esp32JsonParser.parseStatus(json, 0)
        assertFalse(s.gps.fix)
        assertTrue(s.gps.valid)
        assertEquals(51.120450, s.gps.lon!!, 1e-9)
    }

    @Test
    fun zeroCoordinatesAreTreatedAsInvalid() {
        val json = docExample.replace("43.640120", "0").replace("51.120450", "0")
        val s = Esp32JsonParser.parseStatus(json, 0)
        assertFalse(s.gps.valid)
        assertFalse(s.gps.fix)
        assertNull(s.gps.lat)
    }

    @Test
    fun invalidDepthSensorGivesNullDepth() {
        val json = docExample.replace("\"valid\": true, \"depth_m\"", "\"valid\": false, \"depth_m\"")
        val s = Esp32JsonParser.parseStatus(json, 0)
        assertFalse(s.depth.valid)
        assertNull(s.depth.depthM)
    }

    @Test
    fun sosFromButtonIsParsed() {
        val json = docExample.replace("\"active\": false, \"source\": \"none\"", "\"active\": true, \"source\": \"button\"")
        val s = Esp32JsonParser.parseStatus(json, 0)
        assertTrue(s.sos.active)
        assertEquals(SosSource.BUTTON, s.sos.source)
    }

    @Test(expected = Esp32Exception::class)
    fun rejectsMissingApiVersion() {
        Esp32JsonParser.parseStatus("""{"depth":{},"gps":{},"sos":{}}""", 0)
    }

    @Test(expected = Esp32Exception::class)
    fun rejectsUnsupportedApiVersion() {
        Esp32JsonParser.parseStatus(docExample.replace("\"api_version\": 1", "\"api_version\": 2"), 0)
    }

    @Test(expected = Esp32Exception::class)
    fun rejectsMalformedJson() {
        Esp32JsonParser.parseStatus("<html>Not JSON</html>", 0)
    }

    @Test
    fun parsesInfo() {
        val info = Esp32JsonParser.parseInfo(
            """{"api_version":1,"device_name":"AquaRescue Vest","firmware_version":"0.1.0","components":["MS5837-30BA","NEO-6M"]}"""
        )
        assertEquals("AquaRescue Vest", info.deviceName)
        assertEquals("0.1.0", info.firmwareVersion)
        assertEquals(listOf("MS5837-30BA", "NEO-6M"), info.components)
    }
}
