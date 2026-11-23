package com.vinylcast.sonos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SonosXmlBuilderTest {

    @Test
    fun `parseSsdpLocation extracts HTTP location case-insensitively`() {
        val resp = """
            HTTP/1.1 200 OK
            CACHE-CONTROL: max-age = 1800
            location: http://192.168.1.42:1400/xml/device_description.xml
            SERVER: Linux UPnP/1.0 Sonos/79.1-52290 (ZPS9)
        """.trimIndent()

        assertEquals(
            "http://192.168.1.42:1400/xml/device_description.xml",
            SonosXmlBuilder.parseSsdpLocation(resp)
        )
    }

    @Test
    fun `buildDidlLiteMetadata escapes XML characters`() {
        val didl = SonosXmlBuilder.buildDidlLiteMetadata(
            streamUrl = "http://192.168.1.10:8765/stream.wav",
            title = "Vinyl & HiFi"
        )

        assertTrue(didl.contains("<dc:title>Vinyl &amp; HiFi</dc:title>"))
        assertTrue(didl.contains("<dc:creator>Plattenspieler</dc:creator>"))
    }
}
