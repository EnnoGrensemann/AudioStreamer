package com.vinylcast.sonos

import com.vinylcast.model.TrackInfo
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
    fun `buildDidlLiteMetadata escapes XML characters and includes cover URL`() {
        val track = TrackInfo(
            title = "Echoes & Waves",
            artist = "Pink Floyd",
            album = "Meddle",
            coverUrl = "https://example.com/cover.jpg?a=1&b=2"
        )
        val didl = SonosXmlBuilder.buildDidlLiteMetadata("http://192.168.1.10:8765/stream.wav", track)

        assertTrue(didl.contains("<dc:title>Echoes &amp; Waves</dc:title>"))
        assertTrue(didl.contains("<dc:creator>Pink Floyd</dc:creator>"))
        assertTrue(didl.contains("<upnp:albumArtURI>https://example.com/cover.jpg?a=1&amp;b=2</upnp:albumArtURI>"))
    }
}
