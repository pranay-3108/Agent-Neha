package com.sentineldroid

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object PassiveNetworkObservation {
    data class Snapshot(
        val at: Long,
        val vpnActive: Boolean,
        val activeNetworks: Int,
        val note: String
    )

    fun collect(context: Context): Snapshot {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val networks = cm?.allNetworks.orEmpty()
        val vpn = networks.any { cm?.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
        val snap = Snapshot(System.currentTimeMillis(), vpn, networks.size, "Non-decrypting observation only; no HTTPS plaintext or packet interception is claimed.")
        EvidenceVault.get(context).add("PASSIVE_NETWORK_OBSERVATION", "CONNECTIVITY_MANAGER", "PLATFORM_DERIVED", null, null, "application/json", org.json.JSONObject().put("at", snap.at).put("vpnActive", snap.vpnActive).put("activeNetworks", snap.activeNetworks).put("note", snap.note).toString(), "OBSERVATIONAL")
        return snap
    }
}
