package dev.turtur.sos

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.turtur.MainActivity
import dev.turtur.R
import dev.turtur.model.ActiveSos
import dev.turtur.model.SosSighting

object SosNotifier {
    const val CH_BEACON = "turtur_sos_beacon"
    private const val CH_ALERT = "turtur_sos_alert"
    private const val CH_CARRY = "turtur_sos_carry"
    const val BEACON_ID = 41
    private const val CARRY_ID = 42
    private const val HEARD_BASE = 300
    private const val UNREAD_BASE = 500

    const val EXTRA_BEACON = "dev.turtur.extra.SOS_BEACON"
    const val EXTRA_SCREEN = "dev.turtur.extra.SOS_SCREEN"
    const val SCREEN_SOS = "sos"
    const val SCREEN_HEARD = "heard"
    const val SCREEN_FINDER = "finder"

    fun ensureChannels(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        mgr.createNotificationChannel(
            NotificationChannel(CH_BEACON, context.getString(R.string.sos_beacon_channel_name), NotificationManager.IMPORTANCE_LOW)
                .apply { description = context.getString(R.string.sos_beacon_channel_desc) },
        )
        val alarm = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        mgr.createNotificationChannel(
            NotificationChannel(CH_ALERT, context.getString(R.string.sos_alert_channel_name), NotificationManager.IMPORTANCE_HIGH)
                .apply {
                    description = context.getString(R.string.sos_alert_channel_desc)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 600)
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), alarm)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
        )
        mgr.createNotificationChannel(
            NotificationChannel(CH_CARRY, context.getString(R.string.sos_carry_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.sos_carry_channel_desc) },
        )
    }

    fun beaconNotification(context: Context, active: ActiveSos): Notification {
        ensureChannels(context)
        val r = active.record
        val text = when {
            r.cancelled -> context.getString(R.string.sos_beacon_safe_body)
            r.lat != null -> context.getString(R.string.sos_beacon_fix_body, minutesAgo(r.fixUnix))
            else -> context.getString(R.string.sos_beacon_nofix_body)
        }
        return NotificationCompat.Builder(context, CH_BEACON)
            .setSmallIcon(R.drawable.ic_stat_turtur)
            .setContentTitle(context.getString(R.string.sos_beacon_title))
            .setContentText(text)
            .setContentIntent(openApp(context, 1, SCREEN_SOS, null))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun updateBeacon(context: Context, active: ActiveSos) {
        post(context, BEACON_ID, beaconNotification(context, active))
    }

    fun showHeard(context: Context, sighting: SosSighting, loud: Boolean) {
        ensureChannels(context)
        val r = sighting.record
        val title = if (r.cancelled) {
            context.getString(R.string.sos_heard_safe_title, r.name)
        } else {
            context.getString(R.string.sos_heard_title, r.name)
        }
        val trust = context.getString(if (sighting.verified) R.string.sos_verified else R.string.sos_unverified)
        val fix = if (r.lat != null) {
            context.getString(R.string.sos_heard_fix, minutesAgo(r.fixUnix))
        } else {
            context.getString(R.string.sos_heard_nofix)
        }
        val open = openApp(context, r.beaconId.hashCode(), SCREEN_FINDER, r.beaconId)
        val builder = NotificationCompat.Builder(context, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat_turtur)
            .setContentTitle(title)
            .setContentText("$trust · $fix")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$trust · $fix\n${context.getString(R.string.sos_heard_hint)}"))
            .setContentIntent(open)
            .setAutoCancel(false)
            .setSilent(!loud)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (loud && !r.cancelled) builder.setFullScreenIntent(open, true)
        post(context, HEARD_BASE + r.beaconId.hashCode(), builder.build())
    }

    /** Beacon seen but its record could not be read over GATT. Still worth a walk. */
    fun showUnread(context: Context, beaconId: String) {
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat_turtur)
            .setContentTitle(context.getString(R.string.sos_unread_title))
            .setContentText(context.getString(R.string.sos_unread_body))
            .setContentIntent(openApp(context, beaconId.hashCode(), SCREEN_FINDER, beaconId))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        post(context, UNREAD_BASE + beaconId.hashCode(), n)
    }

    fun cancelHeard(context: Context, beaconId: String) {
        NotificationManagerCompat.from(context).cancel(HEARD_BASE + beaconId.hashCode())
        NotificationManagerCompat.from(context).cancel(UNREAD_BASE + beaconId.hashCode())
    }

    fun showCarrying(context: Context, sightings: List<SosSighting>, signalBack: Boolean) {
        ensureChannels(context)
        val first = sightings.first()
        val names = sightings.joinToString(", ") { it.record.name }
        val text = if (signalBack) {
            context.getString(R.string.sos_carry_signal_body, names)
        } else {
            context.getString(R.string.sos_carry_body, names)
        }
        val builder = NotificationCompat.Builder(context, CH_CARRY)
            .setSmallIcon(R.drawable.ic_stat_turtur)
            .setContentTitle(context.getString(R.string.sos_carry_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(context, 2, SCREEN_HEARD, null))
            .setOngoing(true)
            .setSilent(!signalBack)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        SmsBridge.smsIntent(first.record)?.let { sms ->
            val pi = PendingIntent.getActivity(
                context,
                first.record.beaconId.hashCode(),
                sms,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, context.getString(R.string.sos_carry_action, first.record.name), pi)
        }
        post(context, CARRY_ID, builder.build())
    }

    fun cancelCarrying(context: Context) {
        NotificationManagerCompat.from(context).cancel(CARRY_ID)
    }

    private fun openApp(context: Context, requestCode: Int, screen: String, beaconId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_SCREEN, screen)
        beaconId?.let { intent.putExtra(EXTRA_BEACON, it) }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun post(context: Context, id: Int, n: Notification) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }

    fun minutesAgo(unix: Long?): Long =
        if (unix == null) 0 else ((System.currentTimeMillis() - unix) / 60_000L).coerceAtLeast(0)
}
