package com.mdblisthub.tv.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Whether the connection the phone is on right now is one the viewer pays for by
 * the byte.
 *
 * A phone, unlike a television, spends most of its life on mobile data. Two things
 * here download far more than the film being watched needs at that moment: the disk
 * read-ahead, which works up to gigabytes ahead of the picture, and a torrent played
 * from its swarm, which also uploads. Both are worth it on Wi-Fi and a bill on a
 * data plan, so both ask this first.
 *
 * Read live, not cached: the phone moves between networks during a film.
 */
object NetworkMeter {

    @Volatile private var connectivity: ConnectivityManager? = null

    fun install(context: Context) {
        connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    }

    /**
     * True on a metered connection, and when there is no connection at all. Unknown
     * counts as metered: being wrong that way costs a slower start, the other way
     * costs the viewer's data.
     */
    fun isMetered(): Boolean {
        val manager = connectivity ?: return true
        val network = manager.activeNetwork ?: return true
        val capabilities = manager.getNetworkCapabilities(network) ?: return true
        return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
