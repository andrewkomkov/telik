package io.github.henry753951.airplayremote.headphones

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.AttributionSource
import android.os.Looper
import kotlin.system.exitProcess

/** Entry point for app_process. It runs with the rights of the adb shell, not of the app. */
object HeadphonesTool {
    @JvmStatic
    fun main(args: Array<String>) {
        try {
            println("connect=${connect(args.single())}")
        } catch (e: ReflectiveOperationException) {
            println("failed=${e.cause ?: e}")
        } catch (e: IllegalArgumentException) {
            println("failed=$e")
        }
        exitProcess(0)
    }

    @Suppress("DEPRECATION")
    private fun connect(address: String): Any? {
        Looper.prepareMainLooper()
        Class.forName("android.app.ActivityThread").getMethod("initializeMainlineModules").invoke(null)
        val source = AttributionSource.Builder(SHELL_UID).setPackageName(SHELL_PACKAGE).build()
        val adapter = BluetoothAdapter::class.java
            .getMethod("createAdapter", AttributionSource::class.java)
            .invoke(null, source) as BluetoothAdapter
        val device = adapter.getRemoteDevice(address)
        return BluetoothDevice::class.java.getMethod("connect").invoke(device)
    }

    private const val SHELL_UID = 2000
    private const val SHELL_PACKAGE = "com.android.shell"
}
