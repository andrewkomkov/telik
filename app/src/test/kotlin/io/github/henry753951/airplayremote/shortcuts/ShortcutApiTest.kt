package io.github.henry753951.airplayremote.shortcuts

import io.github.henry753951.airplayremote.shortcuts.ShortcutApi.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutApiTest {
    private val token = "ABCD-EFGH-JKLM-NPQR-STUV-WXYZ-23"
    private fun call(target: String, auth: String? = "Bearer $token", method: String = "POST") =
        ShortcutApi.parse(method, target, auth, token)

    @Test fun refusesWithoutTheRightToken() {
        assertEquals(Result.Error(401, "unauthorized"), call("/v1/key?name=play", auth = null))
        assertEquals(Result.Error(401, "unauthorized"), call("/v1/key?name=play", auth = "Bearer WRONG"))
        assertEquals(Result.Error(401, "unauthorized"), ShortcutApi.parse("POST", "/v1/key?name=play", "Bearer ", ""))
    }

    @Test fun tokenIgnoresDashesAndCase() {
        assertTrue(ShortcutApi.authorized("Bearer abcdefghjklmnpqrstuvwxyz23", token))
    }

    @Test fun parsesTheAllowedActions() {
        assertEquals(Result.Ok(ShortcutAction.Open("com.google.android.youtube.tv")), call("/v1/open?app=com.google.android.youtube.tv"))
        assertEquals(Result.Ok(ShortcutAction.Input(2)), call("/v1/input?hdmi=2"))
        assertEquals(Result.Ok(ShortcutAction.Message("Звонят в дверь")), call("/v1/message?text=%D0%97%D0%B2%D0%BE%D0%BD%D1%8F%D1%82+%D0%B2+%D0%B4%D0%B2%D0%B5%D1%80%D1%8C"))
        assertEquals(Result.Ok(ShortcutAction.Key("pause")), call("/v1/key?name=pause", method = "GET"))
    }

    @Test fun refusesBadArguments() {
        assertEquals(Result.Error(400, "app"), call("/v1/open?app=;rm"))
        assertEquals(Result.Error(400, "hdmi"), call("/v1/input?hdmi=0"))
        assertEquals(Result.Error(400, "text"), call("/v1/message?text="))
        assertEquals(Result.Error(400, "name"), call("/v1/key?name=power"))
        assertEquals(Result.Error(404, "path"), call("/v1/shell?cmd=ls"))
        assertEquals(Result.Error(405, "method"), call("/v1/key?name=play", method = "DELETE"))
    }

    @Test fun longMessagesAreCut() {
        val r = call("/v1/message?text=" + "a".repeat(500)) as Result.Ok
        assertEquals(200, (r.action as ShortcutAction.Message).text.length)
    }

    @Test fun newTokenHas128BitsInGroups() {
        val a = ShortcutApi.newToken()
        assertEquals(26, a.replace("-", "").length)
        assertEquals(7, a.split("-").size)
        assertNotEquals(a, ShortcutApi.newToken())
        assertFalse(a.contains('O') || a.contains('I') || a.contains('0') || a.contains('1'))
    }
}
