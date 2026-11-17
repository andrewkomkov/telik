package io.github.henry753951.airplayremote.screensaver

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Locale

class AerialCatalogTest {
    private val entries = """
        {"assets": [
          {"id": "A", "accessibilityLabel": "Seals", "pointsOfInterest": {"0": "A016_C009_0"},
           "url-1080-SDR": "https://sylvan.apple.com/a_HEVC.mov", "url-1080-H264": "https://sylvan.apple.com/a_AVC.mov"},
          {"id": "B", "accessibilityLabel": "Hong Kong", "url-1080-H264": "https://sylvan.apple.com/b_AVC.mov"},
          {"id": "C", "accessibilityLabel": "Broken", "url-1080-H264": "http://insecure/c.mov"}
        ]}
    """.trimIndent()

    @Test fun parsePrefersHevcAndLocalNames() {
        val out = AerialCatalog.parse(entries, mapOf("A016_C009_0" to "Морские котики"), hevc = true)
        assertEquals(listOf("A", "B"), out.map { it.id })
        assertEquals("https://sylvan.apple.com/a_HEVC.mov", out[0].url)
        assertEquals("Морские котики", out[0].place)
        assertEquals("Hong Kong", out[1].place)
    }

    @Test fun parseWithoutHevcUsesH264() {
        val out = AerialCatalog.parse(entries, emptyMap(), hevc = false)
        assertEquals("https://sylvan.apple.com/a_AVC.mov", out[0].url)
        assertEquals("Seals", out[0].place)
    }

    @Test fun stringsPathFallsBackToLanguageThenEnglish() {
        assertEquals(
            listOf(
                "TVIdleScreenStrings.bundle/zh_TW.lproj/Localizable.nocache.strings",
                "TVIdleScreenStrings.bundle/zh.lproj/Localizable.nocache.strings",
                "TVIdleScreenStrings.bundle/en.lproj/Localizable.nocache.strings",
            ),
            AerialCatalog.stringsPath(Locale.TRADITIONAL_CHINESE.let { Locale.Builder().setLanguage("zh").setRegion("TW").build() }),
        )
    }

    private fun tarEntry(name: String, body: ByteArray): ByteArray {
        val header = ByteArray(512)
        name.toByteArray().copyInto(header)
        "%011o".format(body.size).toByteArray().copyInto(header, 124)
        val padded = ByteArray((body.size + 511) / 512 * 512)
        body.copyInto(padded)
        return header + padded
    }

    @Test fun untarReadsOnlyTheWantedFiles() {
        val tar = ByteArrayOutputStream().apply {
            write(tarEntry("skip.bin", ByteArray(700) { 7 }))
            write(tarEntry("entries.json", "{}".toByteArray()))
            write(ByteArray(1024))
        }.toByteArray()
        val out = AerialCatalog.untar(ByteArrayInputStream(tar), setOf("entries.json", "missing"))
        assertEquals(setOf("entries.json"), out.keys)
        assertArrayEquals("{}".toByteArray(), out["entries.json"])
    }

    @Test fun bplistStrings() {
        val data = javaClass.getResourceAsStream("/screensaver/names.bplist")!!.readBytes()
        val map = Bplist.strings(data)
        assertEquals(22, map.size)
        assertEquals("Золотая медуза в водах Палау", map["A001_C007_0"])
        assertEquals("Plain ASCII value", map["ascii_key"])
        assertEquals("value 19", map["k19"])
    }
}
