package ca.cgagnier.wlednativeandroid.ui

import android.content.Context
import android.net.wifi.WifiManager

fun getCurrentSsid(context: Context): String? {
    val wifiManager = context.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null

    val info = wifiManager.connectionInfo ?: return null
    val ssid = info.ssid ?: return null

    // Algunos dispositivos devuelven el SSID entre comillas
    return ssid.replace("\"", "")
}

/**
 * Devuelve true si el teléfono está conectado a un AP cuyo SSID empieza con "WLED".
 * Puedes ajustar esto si tu AP se llama distinto.
 */
fun isConnectedToWledAp(context: Context): Boolean {
    val ssid = getCurrentSsid(context) ?: return false
    return ssid.startsWith("WLED", ignoreCase = true)
}
