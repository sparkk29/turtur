package dev.turtur.sos

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import dev.turtur.model.SosRecord

/**
 * No server: the person carrying an SOS texts the hiker's contacts from their own SMS app.
 * We only prefill the message and nudge them when mobile signal comes back.
 */
object SmsBridge {
    private const val SIGNAL_NUDGE_MS = 30 * 60_000L

    private var callback: ConnectivityManager.NetworkCallback? = null
    private var lastNudgeUnix = 0L

    fun smsIntent(record: SosRecord): Intent? {
        val uri = SmsText.smsUri(record.contacts) ?: return null
        return Intent(Intent.ACTION_SENDTO, Uri.parse(uri))
            .putExtra("sms_body", SmsText.body(record))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun openSms(context: Context, record: SosRecord): Boolean {
        val intent = smsIntent(record) ?: return false
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    @Synchronized
    fun refreshCarrying(context: Context) {
        val app = context.applicationContext
        val carrying = SosRuntime.carrying()
        if (carrying.isEmpty()) {
            SosNotifier.cancelCarrying(app)
            unwatch(app)
            return
        }
        SosNotifier.showCarrying(app, carrying, signalBack = false)
        watch(app)
    }

    private fun watch(context: Context) {
        if (callback != null) return
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val now = System.currentTimeMillis()
                if (now - lastNudgeUnix < SIGNAL_NUDGE_MS) return
                val carrying = SosRuntime.carrying()
                if (carrying.isEmpty()) return
                lastNudgeUnix = now
                SosNotifier.showCarrying(context, carrying, signalBack = true)
            }
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()
        runCatching { cm.registerNetworkCallback(request, cb) }.onSuccess { callback = cb }
    }

    private fun unwatch(context: Context) {
        val cb = callback ?: return
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        runCatching { cm.unregisterNetworkCallback(cb) }
        callback = null
    }
}
