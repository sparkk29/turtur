package dev.turtur.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.turtur.MainActivity
import dev.turtur.R
import dev.turtur.model.Arrival
import dev.turtur.model.PigeonKind

object ArrivalNotifier {
    private const val CHANNEL_ID = "turtur_arrivals"
    private const val BASE_ID = 100

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.arrivals_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.arrivals_channel_desc)
            setShowBadge(true)
        }
        mgr.createNotificationChannel(channel)
    }

    fun notifyArrival(context: Context, arrival: Arrival, senderName: String) {
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val kind = when (arrival.kind) {
            PigeonKind.NOTE -> "Note"
            PigeonKind.MEETUP -> "Meetup"
            PigeonKind.OKAY -> "Okay"
            else -> "Pigeon"
        }
        // Lock screen stays opaque; detail is in the app.
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(context.getString(R.string.arrival_notif_title))
            .setContentText(context.getString(R.string.arrival_notif_body, kind, senderName))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(BASE_ID + arrival.pigeonId.hashCode(), notification)
        }
        haptic(context)
    }

    fun hapticFeed(context: Context) {
        haptic(context, short = true)
    }

    private fun haptic(context: Context, short: Boolean = false) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(VibratorManager::class.java) ?: return
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ms = if (short) 40L else 80L
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(if (short) 40 else 80)
        }
    }
}
