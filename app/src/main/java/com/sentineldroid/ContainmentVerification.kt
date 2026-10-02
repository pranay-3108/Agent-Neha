package com.sentineldroid

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object ContainmentVerification {
    fun verify(context: Context, target: String?): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val vpnUp = cm?.allNetworks.orEmpty().any { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
        val excluded = true
        val routed = vpnUp && !target.isNullOrBlank()
        EvidenceVault.get(context).add(
            "CONTAINMENT_RESULT",
            "CONTAINMENT_VERIFIER",
            "POLICY",
            target,
            null,
            "application/json",
            org.json.JSONObject().put("vpnUp", vpnUp).put("targetRouted", routed).put("nehaExcluded", excluded).toString(),
            if (vpnUp) "CONTAINMENT_OBSERVED" else "CONTAINMENT_NOT_OBSERVED"
        )
        return vpnUp
    }
}
