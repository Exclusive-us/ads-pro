package com.adspro.app.notif
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.adspro.app.AdsProApp
import com.adspro.app.MainActivity
import com.adspro.app.R

object Notifier {
    fun build(context: Context, text: String): Notification {
        val pi = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(context, AdsProApp.CHANNEL_ID).setSmallIcon(R.drawable.ic_sms).setContentTitle("Ads Pro").setContentText(text).setOngoing(true).setSilent(true).setPriority(NotificationCompat.PRIORITY_LOW).setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE).setContentIntent(pi).build()
    }
}
