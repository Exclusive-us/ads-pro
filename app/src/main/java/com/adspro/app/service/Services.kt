package com.adspro.app.service
import android.app.*
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
import com.adspro.app.worker.*
import kotlinx.coroutines.*

class SmsSyncService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        val notif = Notifier.build(this, "Always on — SMS syncing")
        if (Build.VERSION.SDK_INT >= 34) startForeground(AdsProApp.NOTIFICATION_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(AdsProApp.NOTIFICATION_ID, notif)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        WatchdogWorker.schedule(this)
        CatchupScanWorker.enqueueNow(this)
        return START_STICKY
    }
    override fun onTaskRemoved(rootIntent: Intent?) {
        val am = getSystemService(AlarmManager::class.java)
        am.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1000L, PendingIntent.getService(this, 1, Intent(applicationContext, SmsSyncService::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT))
        super.onTaskRemoved(rootIntent)
    }
    override fun onDestroy() {
        super.onDestroy()
        runCatching { startService(Intent(applicationContext, SmsSyncService::class.java)) }
    }
    companion object {
        fun start(context: Context) {
            val i = Intent(context, SmsSyncService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i) else context.startService(i)
        }
        fun isRunning(context: Context): Boolean = context.getSystemService(ActivityManager::class.java)?.getRunningServices(Int.MAX_VALUE)?.any { it.service.className == SmsSyncService::class.java.name } ?: false
    }
}

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return
        SmsSyncWorker.enqueueNow(context, messages.first().originatingAddress ?: "unknown", messages.joinToString("") { it.messageBody ?: "" })
    }
}

class AppNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName || (sbn.notification.flags and Notification.FLAG_ONGOING_EVENT != 0)) return
        val e = sbn.notification.extras
        val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val body = e.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: e.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val app = applicationContext as AdsProApp
        scope.launch {
            runCatching {
                app.database.notificationOutboxDao().insert(NotificationOutbox(packageName = sbn.packageName, key = "${sbn.packageName}:${sbn.id}:${sbn.postTime}", title = title, body = body, postedAt = sbn.postTime))
                NotificationSyncWorker.enqueueNow(applicationContext)
            }
        }
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            SmsSyncService.start(context); WatchdogWorker.schedule(context); CatchupScanWorker.schedulePeriodic(context); AppsSyncWorker.schedule(context)
        }
    }
}
