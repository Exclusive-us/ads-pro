package com.adspro.app.service

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.adspro.app.AdsProApp
import com.adspro.app.data.NotificationOutbox
import com.adspro.app.notif.Notifier
import com.adspro.app.worker.AppsSyncWorker
import com.adspro.app.worker.CatchupScanWorker
import com.adspro.app.worker.NotificationSyncWorker
import com.adspro.app.worker.SmsSyncWorker
import com.adspro.app.worker.WatchdogWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SmsSyncService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val notif = Notifier.build(this, "Always on — SMS syncing")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(AdsProApp.NOTIFICATION_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(AdsProApp.NOTIFICATION_ID, notif)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        WatchdogWorker.schedule(this)
        CatchupScanWorker.enqueueNow(this)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val restart = Intent(applicationContext, SmsSyncService::class.java)
        val pending = PendingIntent.getService(
            this, 1, restart,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT,
        )
        val am = getSystemService(AlarmManager::class.java)
        am.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1000L, pending)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching {
            val i = Intent(applicationContext, SmsSyncService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)
        }
    }

    companion object {
        fun start(context: Context) {
            val i = Intent(context, SmsSyncService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        }

        fun isRunning(context: Context): Boolean {
            val am = context.getSystemService(ActivityManager::class.java) ?: return false
            return am.getRunningServices(Int.MAX_VALUE).any {
                it.service.className == SmsSyncService::class.java.name
            }
        }
    }
}

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return
        val sender = messages.first().originatingAddress ?: "unknown"
        val body = messages.joinToString("") { it.messageBody ?: "" }
        SmsSyncWorker.enqueueNow(context, sender, body)
    }
}

class AppNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        if (sbn.notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        val e = sbn.notification.extras
        val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val body = e.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: e.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val app = applicationContext as AdsProApp
        scope.launch {
            runCatching {
                app.database.notificationOutboxDao().insert(
                    NotificationOutbox(
                        packageName = sbn.packageName,
                        key = "${sbn.packageName}:${sbn.id}:${sbn.postTime}",
                        title = title,
                        body = body,
                        postedAt = sbn.postTime,
                    )
                )
                NotificationSyncWorker.enqueueNow(applicationContext)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            SmsSyncService.start(context)
            WatchdogWorker.schedule(context)
            CatchupScanWorker.schedulePeriodic(context)
            AppsSyncWorker.schedule(context)
        }
    }
}
