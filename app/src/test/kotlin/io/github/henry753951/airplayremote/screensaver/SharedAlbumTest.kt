package io.github.henry753951.airplayremote.screensaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedAlbumTest {
    @Test fun tokenFromLink() {
        assertEquals("B0aGWZuqDGKPsDJ", SharedAlbum.token("https://www.icloud.com/sharedalbum/#B0aGWZuqDGKPsDJ"))
        assertEquals("B0aGWZuqDGKPsDJ", SharedAlbum.token("  https://www.icloud.com/sharedalbum/ru-ru/#B0aGWZuqDGKPsDJ "))
        assertNull(SharedAlbum.token("https://www.icloud.com/sharedalbum/"))
        assertNull(SharedAlbum.token("https://evil/#../../x"))
    }

    @Test fun redirectHostOnlyToIcloud() {
        assertEquals("p48-sharedstreams.icloud.com", SharedAlbum.redirectHost("""{"X-Apple-MMe-Host":"p48-sharedstreams.icloud.com"}"""))
        assertNull(SharedAlbum.redirectHost("""{"X-Apple-MMe-Host":"evil.example.com"}"""))
        assertNull(SharedAlbum.redirectHost("""{"X-Apple-MMe-Host":"x.icloud.com/../evil"}"""))
        assertNull(SharedAlbum.redirectHost("""{"photos":[]}"""))
    }

    private val stream = """
        {"photos": [
          {"photoGuid": "G1", "derivatives": {
             "342": {"checksum": "small1", "fileSize": "20000", "width": "342"},
             "2048": {"checksum": "big1", "fileSize": "900000", "width": "2048"}}},
          {"photoGuid": "G2", "mediaAssetType": "video", "derivatives": {"720p": {"checksum": "v", "fileSize": "5"}}},
          {"photoGuid": "G3", "derivatives": {"1536": {"checksum": "big3", "fileSize": 500000}}}
        ]}
    """.trimIndent()

    @Test fun largestVersionOfEachPhoto() {
        assertEquals(listOf("G1" to "big1", "G3" to "big3"), SharedAlbum.photos(stream))
    }

    @Test fun downloadLinks() {
        val answer = """
            {"items": {
              "big1": {"url_location": "cvws.icloud-content.com", "url_path": "/S/abc/IMG_1.JPG?o=x"},
              "bad": {"url_location": "evil.com/x", "url_path": "/y"}
            }}
        """.trimIndent()
        assertEquals(mapOf("big1" to "https://cvws.icloud-content.com/S/abc/IMG_1.JPG?o=x"), SharedAlbum.urls(answer))
    }
}
