package io.github.henry753951.airplayremote.platform

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import java.net.Inet4Address
import java.net.NetworkInterface

object LocalNetworkAddress {
    fun get(context: Context): String? {
        // 1. Try ConnectivityManager on API 23+
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = cm?.activeNetwork
                if (network != null) {
                    val linkProps = cm.getLinkProperties(network)
                    if (linkProps != null) {
                        for (linkAddr in linkProps.linkAddresses) {
                            val addr = linkAddr.address
                            if (addr is Inet4Address && !addr.isLoopbackAddress) {
                                val host = addr.hostAddress
                                if (!host.isNullOrBlank() && host != "0.0.0.0" && host != "127.0.0.1") {
                                    return host
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Iterate NetworkInterfaces (works for both Wi-Fi and Ethernet on all Android versions)
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                for (addr in intf.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress
                        if (!host.isNullOrBlank() && host != "0.0.0.0" && host != "127.0.0.1") {
                            return host
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return null
    }

}
