package com.infinitii.m4td.gps.upload

import com.infinitii.m4td.gps.data.CalTopoConfig
import com.infinitii.m4td.gps.data.LocationFix
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CalTopoPositionReporterTest {

    @Test
    fun `lat lng use dot decimals under a comma-decimal locale`() {
        val prev = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        try {
            val server = MockWebServer().apply {
                enqueue(MockResponse().setResponseCode(200))
                start()
            }
            val cfg = CalTopoConfig(
                baseUrl = server.url("/").toString().removeSuffix("/"),
                connectKey = "M4TDTEST",
                deviceId = "1",
                reportIntervalSeconds = 5,
                skipInvalidFixes = true,
            )
            val fix = LocationFix(
                deviceId = "1",
                lat = 49.323750,
                lon = -117.659300,
                altitudeMeters = 0.0,
            )
            CalTopoPositionReporter(OkHttpClient(), cfg).report(fix)

            val path = server.takeRequest().path!!
            assertTrue("path was: $path", path.contains("lat=49.323750"))
            assertTrue("path was: $path", path.contains("lng=-117.659300"))
            server.shutdown()
        } finally {
            Locale.setDefault(prev)
        }
    }
}
