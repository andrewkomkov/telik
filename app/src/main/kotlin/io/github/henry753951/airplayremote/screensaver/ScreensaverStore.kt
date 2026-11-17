package io.github.henry753951.airplayremote.screensaver

import android.content.Context

/** The album link lives outside the main settings, so the settings list of adb does not show it (FR-008). */
object ScreensaverStore {
    private const val FILE = "screensaver"
    private const val ALBUM = "album_link"

    fun albumLink(context: Context): String? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(ALBUM, null)?.takeIf { SharedAlbum.token(it) != null }

    fun setAlbumLink(context: Context, link: String?) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().apply {
            if (link.isNullOrBlank()) remove(ALBUM) else putString(ALBUM, link.trim())
        }.apply()
    }
}
