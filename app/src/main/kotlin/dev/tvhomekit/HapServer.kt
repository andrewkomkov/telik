package dev.tvhomekit

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "TvHomeKit"
private const val JSON = "application/hap+json"
private const val PAIRING = "application/pairing+tlv8"
private const val MAX_FRAME = 1024

internal class HapServer(
    private val store: Store,
    private val database: HapDatabase,
    private val onPairingChanged: () -> Unit,
) {
    private val connections = CopyOnWriteArrayList<Connection>()
    private var setup: SrpServer? = null
    private val longTermPublic = HapCrypto.ed25519Public(store.seed)
    private val accessoryId = store.mac.toByteArray()

    fun start(port: Int) {
        val server = ServerSocket(port)
        Thread({
            while (true) {
                val socket = server.accept()
                socket.tcpNoDelay = true
                Thread({ Connection(socket).run() }, "hap-connection").start()
            }
        }, "hap-accept").start()
    }

    fun notifyChanged(iid: Int) {
        connections.forEach { it.sendEvent(iid) }
    }

    private inner class Connection(private val socket: Socket) {
        private val output = socket.getOutputStream()
        private val input = socket.getInputStream()
        private var readKey: ByteArray? = null
        private var writeKey: ByteArray? = null
        private var readCounter = 0L
        private var writeCounter = 0L
        private var decrypted = ByteArray(0)
        private var curve: HapCrypto.Curve? = null
        private var peerCurve = ByteArray(0)
        private var verifyKey = ByteArray(0)
        private var shared = ByteArray(0)
        private val subscriptions = HashSet<Int>()

        fun run() {
            connections += this
            try {
                while (true) handle(readRequest())
            } catch (e: EOFException) {
                Log.d(TAG, "Клиент отключился")
            } catch (e: Exception) {
                Log.w(TAG, "Соединение закрыто: $e")
            } finally {
                connections -= this
                runCatching { socket.close() }
            }
        }

        private fun readExact(stream: InputStream, size: Int): ByteArray {
            val out = ByteArray(size)
            var offset = 0
            while (offset < size) {
                val read = stream.read(out, offset, size - offset)
                if (read < 0) throw EOFException()
                offset += read
            }
            return out
        }

        private fun nextByte(): Int {
            val key = readKey ?: return input.read().also { if (it < 0) throw EOFException() }
            if (decrypted.isEmpty()) {
                val header = readExact(input, 2)
                val length = (header[0].toInt() and 0xFF) or ((header[1].toInt() and 0xFF) shl 8)
                val sealed = readExact(input, length + 16)
                decrypted = HapCrypto.open(key, HapCrypto.nonce(readCounter++), sealed, header)
                    ?: throw IllegalStateException("Неверный код подлинности кадра")
            }
            val value = decrypted[0].toInt() and 0xFF
            decrypted = decrypted.copyOfRange(1, decrypted.size)
            return value
        }

        private fun readRequest(): Request {
            val head = ByteArrayOutputStream()
            while (!head.toByteArray().endsWith("\r\n\r\n")) head.write(nextByte())
            val lines = String(head.toByteArray()).split("\r\n")
            val (method, target) = lines[0].split(" ")
            val length = lines.firstOrNull { it.startsWith("content-length:", ignoreCase = true) }
                ?.substringAfter(':')?.trim()?.toInt() ?: 0
            return Request(method, target, ByteArray(length) { nextByte().toByte() })
        }

        private fun ByteArray.endsWith(tail: String): Boolean {
            val bytes = tail.toByteArray()
            return size >= bytes.size && copyOfRange(size - bytes.size, size).contentEquals(bytes)
        }

        private fun handle(request: Request) {
            val path = request.target.substringBefore('?')
            val secure = readKey != null
            when {
                path == "/pair-setup" -> respond(200, PAIRING, pairSetup(Tlv.decode(request.body)))
                path == "/pair-verify" -> pairVerify(Tlv.decode(request.body))
                path == "/identify" -> respond(if (store.hasUser()) 400 else 204)
                !secure -> respond(470)
                path == "/pairings" -> respond(200, PAIRING, pairings(Tlv.decode(request.body)))
                path == "/accessories" -> respond(200, JSON, database.accessories().toString().toByteArray())
                path == "/characteristics" && request.method == "GET" -> readCharacteristics(request.target)
                path == "/characteristics" && request.method == "PUT" -> writeCharacteristics(request.body)
                else -> respond(404)
            }
        }

        @Synchronized
        private fun send(message: ByteArray) {
            val key = writeKey
            if (key == null) {
                output.write(message)
            } else {
                var offset = 0
                while (offset < message.size) {
                    val length = minOf(MAX_FRAME, message.size - offset)
                    val header = byteArrayOf(length.toByte(), (length shr 8).toByte())
                    val plain = message.copyOfRange(offset, offset + length)
                    output.write(header)
                    output.write(HapCrypto.seal(key, HapCrypto.nonce(writeCounter++), plain, header))
                    offset += length
                }
            }
            output.flush()
        }

        private fun respond(status: Int, type: String? = null, body: ByteArray = ByteArray(0)) {
            val reason = when (status) {
                200 -> "OK"
                204 -> "No Content"
                207 -> "Multi-Status"
                400 -> "Bad Request"
                404 -> "Not Found"
                470 -> "Connection Authorization Required"
                else -> "Error"
            }
            val head = StringBuilder("HTTP/1.1 $status $reason\r\n")
            if (type != null) head.append("Content-Type: $type\r\n")
            if (status != 204) head.append("Content-Length: ${body.size}\r\n")
            head.append("\r\n")
            send(head.toString().toByteArray() + body)
        }

        fun sendEvent(iid: Int) {
            if (iid !in subscriptions) return
            val body = JSONObject().put(
                "characteristics",
                JSONArray().put(JSONObject().put("aid", 1).put("iid", iid).put("value", database.read(iid))),
            ).toString().toByteArray()
            val head = "EVENT/1.0 200 OK\r\nContent-Type: $JSON\r\nContent-Length: ${body.size}\r\n\r\n"
            runCatching { send(head.toByteArray() + body) }
        }

        private fun readCharacteristics(target: String) {
            val query = target.substringAfter('?', "")
            val ids = query.split('&').first { it.startsWith("id=") }.substringAfter('=').split(',')
            val list = JSONArray()
            for (id in ids) {
                val iid = id.substringAfter('.').toInt()
                list.put(JSONObject().put("aid", 1).put("iid", iid).put("value", database.read(iid)))
            }
            respond(200, JSON, JSONObject().put("characteristics", list).toString().toByteArray())
        }

        private fun writeCharacteristics(body: ByteArray) {
            val list = JSONObject(String(body)).getJSONArray("characteristics")
            for (i in 0 until list.length()) {
                val item = list.getJSONObject(i)
                val iid = item.getInt("iid")
                if (item.has("ev")) {
                    if (toBoolean(item.get("ev"))) subscriptions += iid else subscriptions -= iid
                }
                if (item.has("value")) database.write(iid, item.get("value"))
            }
            respond(204)
        }

        private fun pairSetup(tlv: Map<Int, ByteArray>): ByteArray {
            val state = tlv[Tlv.STATE]?.firstOrNull()?.toInt()
            return when (state) {
                1 -> {
                    if (store.hasUser()) return failure(2, Tlv.ERROR_UNAVAILABLE)
                    val srp = SrpServer(store.pin).also { setup = it }
                    Tlv.encode(
                        Tlv.STATE to Tlv.state(2),
                        Tlv.SALT to srp.salt,
                        Tlv.PUBLIC_KEY to srp.publicKey,
                    )
                }
                3 -> {
                    val srp = setup ?: return failure(4, Tlv.ERROR_AUTHENTICATION)
                    if (!srp.verifyClient(tlv.getValue(Tlv.PUBLIC_KEY), tlv.getValue(Tlv.PROOF))) {
                        Log.w(TAG, "Неверный код сопряжения")
                        return failure(4, Tlv.ERROR_AUTHENTICATION)
                    }
                    Tlv.encode(Tlv.STATE to Tlv.state(4), Tlv.PROOF to srp.serverProof())
                }
                5 -> finishSetup(tlv)
                else -> failure(2, Tlv.ERROR_UNAVAILABLE)
            }
        }

        private fun finishSetup(tlv: Map<Int, ByteArray>): ByteArray {
            val srp = setup ?: return failure(6, Tlv.ERROR_AUTHENTICATION)
            val key = HapCrypto.hkdf(srp.sessionKey, "Pair-Setup-Encrypt-Salt", "Pair-Setup-Encrypt-Info")
            val plain = HapCrypto.open(key, HapCrypto.nonce("PS-Msg05"), tlv.getValue(Tlv.ENCRYPTED))
                ?: return failure(6, Tlv.ERROR_AUTHENTICATION)
            val client = Tlv.decode(plain)
            val id = client.getValue(Tlv.IDENTIFIER)
            val publicKey = client.getValue(Tlv.PUBLIC_KEY)
            val proof = HapCrypto.hkdf(
                srp.sessionKey, "Pair-Setup-Controller-Sign-Salt", "Pair-Setup-Controller-Sign-Info",
            ) + id + publicKey
            if (!HapCrypto.ed25519Verify(publicKey, proof, client.getValue(Tlv.SIGNATURE))) {
                return failure(6, Tlv.ERROR_AUTHENTICATION)
            }
            store.createUser(String(id), publicKey, true)
            setup = null
            val info = HapCrypto.hkdf(
                srp.sessionKey, "Pair-Setup-Accessory-Sign-Salt", "Pair-Setup-Accessory-Sign-Info",
            ) + accessoryId + longTermPublic
            val reply = Tlv.encode(
                Tlv.IDENTIFIER to accessoryId,
                Tlv.PUBLIC_KEY to longTermPublic,
                Tlv.SIGNATURE to HapCrypto.ed25519Sign(store.seed, info),
            )
            Log.i(TAG, "Сопряжение завершено")
            onPairingChanged()
            return Tlv.encode(
                Tlv.STATE to Tlv.state(6),
                Tlv.ENCRYPTED to HapCrypto.seal(key, HapCrypto.nonce("PS-Msg06"), reply),
            )
        }

        private fun pairVerify(tlv: Map<Int, ByteArray>) {
            when (tlv[Tlv.STATE]?.firstOrNull()?.toInt()) {
                1 -> {
                    val own = HapCrypto.Curve().also { curve = it }
                    peerCurve = tlv.getValue(Tlv.PUBLIC_KEY)
                    shared = own.shared(peerCurve)
                    verifyKey = HapCrypto.hkdf(shared, "Pair-Verify-Encrypt-Salt", "Pair-Verify-Encrypt-Info")
                    val signed = own.publicKey + accessoryId + peerCurve
                    val inner = Tlv.encode(
                        Tlv.IDENTIFIER to accessoryId,
                        Tlv.SIGNATURE to HapCrypto.ed25519Sign(store.seed, signed),
                    )
                    respond(
                        200,
                        PAIRING,
                        Tlv.encode(
                            Tlv.STATE to Tlv.state(2),
                            Tlv.PUBLIC_KEY to own.publicKey,
                            Tlv.ENCRYPTED to HapCrypto.seal(verifyKey, HapCrypto.nonce("PV-Msg02"), inner),
                        ),
                    )
                }
                3 -> {
                    val own = curve
                    val plain = HapCrypto.open(verifyKey, HapCrypto.nonce("PV-Msg03"), tlv.getValue(Tlv.ENCRYPTED))
                    val client = plain?.let(Tlv::decode)
                    val id = client?.get(Tlv.IDENTIFIER)
                    val publicKey = id?.let { store.userPublicKey(String(it)) }
                    val valid = own != null && client != null && id != null && publicKey != null &&
                        HapCrypto.ed25519Verify(
                            publicKey, peerCurve + id + own.publicKey, client.getValue(Tlv.SIGNATURE),
                        )
                    if (!valid) {
                        respond(200, PAIRING, failure(4, Tlv.ERROR_AUTHENTICATION))
                        return
                    }
                    respond(200, PAIRING, Tlv.encode(Tlv.STATE to Tlv.state(4)))
                    writeKey = HapCrypto.hkdf(shared, "Control-Salt", "Control-Read-Encryption-Key")
                    readKey = HapCrypto.hkdf(shared, "Control-Salt", "Control-Write-Encryption-Key")
                }
                else -> respond(400)
            }
        }

        private fun pairings(tlv: Map<Int, ByteArray>): ByteArray {
            when (tlv[Tlv.METHOD]?.firstOrNull()?.toInt()) {
                3 -> {
                    val admin = (tlv[Tlv.PERMISSIONS]?.firstOrNull()?.toInt() ?: 0) == 1
                    store.createUser(String(tlv.getValue(Tlv.IDENTIFIER)), tlv.getValue(Tlv.PUBLIC_KEY), admin)
                }
                4 -> {
                    store.removeUser(String(tlv.getValue(Tlv.IDENTIFIER)))
                    onPairingChanged()
                }
                5 -> {
                    val items = ArrayList<Pair<Int, ByteArray>>()
                    items += Tlv.STATE to Tlv.state(2)
                    store.users().forEachIndexed { i, user ->
                        if (i > 0) items += Tlv.SEPARATOR to ByteArray(0)
                        items += Tlv.IDENTIFIER to user.toByteArray()
                        items += Tlv.PUBLIC_KEY to store.userPublicKey(user)!!
                        items += Tlv.PERMISSIONS to byteArrayOf(if (store.userIsAdmin(user)) 1 else 0)
                    }
                    return Tlv.encode(*items.toTypedArray())
                }
            }
            return Tlv.encode(Tlv.STATE to Tlv.state(2))
        }

        private fun failure(state: Int, code: Int) =
            Tlv.encode(Tlv.STATE to Tlv.state(state), Tlv.ERROR to byteArrayOf(code.toByte()))
    }

    private class Request(val method: String, val target: String, val body: ByteArray)
}

internal fun toBoolean(value: Any?): Boolean = when (value) {
    is Boolean -> value
    is Number -> value.toInt() != 0
    is String -> value == "1" || value.equals("true", ignoreCase = true)
    else -> false
}

internal fun toInt(value: Any?): Int = when (value) {
    is Boolean -> if (value) 1 else 0
    is Number -> value.toInt()
    is String -> value.toIntOrNull() ?: 0
    else -> 0
}

internal class Characteristic(
    val iid: Int,
    val type: String,
    val format: String,
    val perms: List<String>,
    val read: (() -> Any)? = null,
    val write: ((Any?) -> Unit)? = null,
)

internal class HapService(
    val iid: Int,
    val type: String,
    val characteristics: List<Characteristic>,
    val primary: Boolean = false,
    val linked: List<Int> = emptyList(),
)

internal class HapDatabase(private val services: List<HapService>) {
    private val byId = services.flatMap { it.characteristics }.associateBy { it.iid }

    fun read(iid: Int): Any = byId[iid]?.read?.invoke() ?: JSONObject.NULL

    fun write(iid: Int, value: Any?) {
        byId[iid]?.write?.invoke(value)
    }

    fun accessories(): JSONObject {
        val list = JSONArray()
        for (service in services) {
            val characteristics = JSONArray()
            for (item in service.characteristics) {
                val json = JSONObject()
                    .put("iid", item.iid)
                    .put("type", fullType(item.type))
                    .put("format", item.format)
                    .put("perms", JSONArray(item.perms))
                item.read?.let { json.put("value", it()) }
                characteristics.put(json)
            }
            val json = JSONObject()
                .put("iid", service.iid)
                .put("type", fullType(service.type))
                .put("characteristics", characteristics)
            if (service.primary) json.put("primary", true)
            if (service.linked.isNotEmpty()) json.put("linked", JSONArray(service.linked))
            list.put(json)
        }
        return JSONObject().put(
            "accessories",
            JSONArray().put(JSONObject().put("aid", 1).put("services", list)),
        )
    }

    private fun fullType(short: String) = "${short.padStart(8, '0')}-0000-1000-8000-0026BB765291"
}
