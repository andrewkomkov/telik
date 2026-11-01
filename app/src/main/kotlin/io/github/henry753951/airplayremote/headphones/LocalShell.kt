package io.github.henry753951.airplayremote.headphones

import android.content.Context
import android.util.Log
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.security.GeneralSecurityException

internal sealed interface ShellResult {
    data class Done(val exitCode: Int, val output: String) : ShellResult
    data object NoDebugging : ShellResult
    data object NeedsApproval : ShellResult
}

/** Adb client for the adb port of this TV. The first use shows the system approval dialog. */
internal class LocalShell(context: Context) {
    private val privateKey = File(context.filesDir, "adb/adbkey")
    private val publicKey = File(context.filesDir, "adb/adbkey.pub")

    fun run(command: String): ShellResult = try {
        Dadb.create(HOST, PORT, keys(), CONNECT_TIMEOUT_MS, APPROVAL_TIMEOUT_MS).use { adb ->
            val response = adb.shell(command)
            ShellResult.Done(response.exitCode, response.allOutput)
        }
    } catch (_: ConnectException) {
        ShellResult.NoDebugging
    } catch (_: IOException) {
        ShellResult.NeedsApproval
    } catch (e: GeneralSecurityException) {
        Log.w(TAG, "Adb key is not usable", e)
        ShellResult.NeedsApproval
    } catch (e: RuntimeException) {
        Log.w(TAG, "Adb client failed", e)
        ShellResult.NeedsApproval
    }

    private fun keys(): AdbKeyPair {
        if (!privateKey.isFile || !publicKey.isFile) {
            privateKey.parentFile?.mkdirs()
            AdbKeyPair.generate(privateKey, publicKey)
        }
        return AdbKeyPair.read(privateKey, publicKey)
    }

    private companion object {
        const val TAG = "LocalShell"
        const val HOST = "127.0.0.1"
        const val PORT = 5555
        const val CONNECT_TIMEOUT_MS = 3_000
        const val APPROVAL_TIMEOUT_MS = 60_000
    }
}
