package com.vinylcast.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcrCloudParserTest {

    @Test
    fun `parseAcrResponse extracts track metadata on status 0`() {
        val json = """
            {
              "status": { "msg": "Success", "code": 0 },
              "metadata": {
                "music": [
                  {
                    "title": "Money",
                    "artists": [{ "name": "Pink Floyd" }],
                    "album": { "name": "The Dark Side of the Moon" },
                    "release_date": "1973-03-01"
                  }
                ]
              }
            }
        """.trimIndent()

        val result = AcrCloudRecognizer.parseAcrResponse(json)
        assertTrue(result is AcrCloudRecognizer.ParsedAcrResult.Match)
        val track = (result as AcrCloudRecognizer.ParsedAcrResult.Match).track
        assertEquals("Money", track.title)
        assertEquals("Pink Floyd", track.artist)
        assertEquals("The Dark Side of the Moon", track.album)
        assertEquals("1973", track.releaseYear)
    }

    @Test
    fun `parseAcrResponse returns NoMatch on status 1001`() {
        val json = """{ "status": { "msg": "No result", "code": 1001 } }"""
        val result = AcrCloudRecognizer.parseAcrResponse(json)
        assertTrue(result is AcrCloudRecognizer.ParsedAcrResult.NoMatch)
    }
}
