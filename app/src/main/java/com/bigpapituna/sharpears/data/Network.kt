package com.bigpapituna.sharpears.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** The network checks the app's two senders share: the review reports ([Reporter]) and the AI brief ([BriefAi]). */
object Network {
    /** True on Wi-Fi or another network that is not metered and reaches the internet. False offline or on mobile data. */
    fun unmetered(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
