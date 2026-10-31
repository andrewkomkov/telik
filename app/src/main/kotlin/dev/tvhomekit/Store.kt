package dev.tvhomekit

import android.content.Context
import android.util.Base64
import java.security.SecureRandom

/** Ключи HomeKit, сопряжения и номера входов: всё это должно переживать перезагрузку. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("homekit", Context.MODE_PRIVATE)

    init {
        if (!prefs.contains("pin")) {
            prefs.edit()
                .putString("pin", generatePin())
                .putString("setupId", generateSetupId())
                .putString("mac", generateMac())
                .putString("key", encode(HapCrypto.randomBytes(32)))
                .commit()
        }
    }

    val pin: String get() = prefs.getString("pin", null)!!
    internal val mac: String get() = prefs.getString("mac", null)!!
    internal val setupId: String get() = prefs.getString("setupId", null)!!
    internal val seed: ByteArray get() = decode(prefs.getString("key", null)!!)

    fun hasUser() = users().isNotEmpty()

    /** Формат полезной нагрузки QR-кода HomeKit: категория, признак IP и код. */
    fun setupUri(category: Int): String {
        val code = (category.toLong() shl 31) or (1L shl 28) or pin.replace("-", "").toLong()
        return "X-HM://" + code.toString(36).padStart(9, '0').uppercase() + setupId
    }

    internal fun users() = prefs.all.keys.filter { it.startsWith(USER) }.map { it.removePrefix(USER) }

    internal fun createUser(id: String, publicKey: ByteArray, admin: Boolean) {
        prefs.edit().putString(USER + id, encode(publicKey)).putBoolean("admin:$id", admin).commit()
    }

    internal fun removeUser(id: String) {
        prefs.edit().remove(USER + id).remove("admin:$id").commit()
    }

    internal fun userPublicKey(id: String) = prefs.getString(USER + id, null)?.let(::decode)

    internal fun userIsAdmin(id: String) = prefs.getBoolean("admin:$id", true)

    internal fun inputId(packageName: String): Int {
        val key = "input:$packageName"
        var id = prefs.getInt(key, 0)
        if (id == 0) {
            id = prefs.getInt("nextInput", 2)
            prefs.edit().putInt(key, id).putInt("nextInput", id + 1).commit()
        }
        return id
    }

    internal fun name(key: String, fallback: String) = prefs.getString("name:$key", fallback)!!

    internal fun setName(key: String, value: String) {
        prefs.edit().putString("name:$key", value).commit()
    }

    /** iPhone перечитывает устройство, только когда этот номер меняется. */
    internal fun configurationIndex(fingerprint: String): Int {
        var index = prefs.getInt("configIndex", 1)
        if (fingerprint != prefs.getString("configHash", null)) {
            if (prefs.contains("configHash")) index++
            prefs.edit().putInt("configIndex", index).putString("configHash", fingerprint).commit()
        }
        return index
    }

    private companion object {
        const val USER = "user:"
        val random = SecureRandom()

        fun encode(value: ByteArray) = Base64.encodeToString(value, Base64.NO_WRAP)
        fun decode(value: String) = Base64.decode(value, Base64.NO_WRAP)

        fun generatePin(): String {
            val banned = setOf(
                "00000000", "11111111", "22222222", "33333333", "44444444", "55555555",
                "66666666", "77777777", "88888888", "99999999", "12345678", "87654321",
            )
            while (true) {
                val digits = random.nextInt(100_000_000).toString().padStart(8, '0')
                if (digits !in banned) {
                    return "${digits.substring(0, 3)}-${digits.substring(3, 5)}-${digits.substring(5)}"
                }
            }
        }

        fun generateSetupId(): String {
            val alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
            return (1..4).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
        }

        fun generateMac(): String {
            val bytes = HapCrypto.randomBytes(6)
            bytes[0] = ((bytes[0].toInt() and 0xFC) or 0x02).toByte()
            return bytes.joinToString(":") { "%02X".format(it) }
        }
    }
}
