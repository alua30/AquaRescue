package com.aquarescue.app.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PhoneWifiState(
    val connected: Boolean,
    /** Есть ли у Wi-Fi-сети интернет (у точки доступа ESP32 его обычно нет — это нормально). */
    val hasInternet: Boolean,
    val network: Network?,
)

/**
 * Отслеживает Wi-Fi телефона. Нужен по двум причинам:
 * 1) индикатор Wi-Fi на главном экране;
 * 2) привязка HTTP-запросов к Wi-Fi: если ESP32 работает точкой доступа без интернета,
 *    Android может отправлять трафик через мобильную сеть, и ESP32 будет «недоступен».
 */
class NetworkMonitor(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _wifiState = MutableStateFlow(PhoneWifiState(false, false, null))
    val wifiState: StateFlow<PhoneWifiState> = _wifiState.asStateFlow()

    private val wifiNetworks = linkedMapOf<Network, NetworkCapabilities?>()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            synchronized(wifiNetworks) {
                wifiNetworks[network] = connectivityManager.getNetworkCapabilities(network)
            }
            publish()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            synchronized(wifiNetworks) { wifiNetworks[network] = networkCapabilities }
            publish()
        }

        override fun onLost(network: Network) {
            synchronized(wifiNetworks) { wifiNetworks.remove(network) }
            publish()
        }
    }

    init {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            // Не требуем интернет: сеть точки доступа ESP32 его не предоставляет.
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            connectivityManager.registerNetworkCallback(request, callback)
        } catch (_: SecurityException) {
            // Нет разрешения ACCESS_NETWORK_STATE — индикатор останется «нет Wi-Fi».
        }
    }

    private fun publish() {
        val entry = synchronized(wifiNetworks) { wifiNetworks.entries.lastOrNull()?.toPair() }
        _wifiState.value = if (entry == null) {
            PhoneWifiState(connected = false, hasInternet = false, network = null)
        } else {
            PhoneWifiState(
                connected = true,
                hasInternet = entry.second?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
                network = entry.first,
            )
        }
    }
}
