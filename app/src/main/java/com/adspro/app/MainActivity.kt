package com.adspro.app
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import com.adspro.app.service.SmsSyncService
import com.adspro.app.ui.AppRoot
import com.adspro.app.worker.AppsSyncWorker
import com.adspro.app.worker.CatchupScanWorker
import com.adspro.app.worker.WatchdogWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED) {
            SmsSyncService.start(this)
            WatchdogWorker.schedule(this)
            CatchupScanWorker.schedulePeriodic(this)
            AppsSyncWorker.schedule(this)
        }
        setContent { AppRoot() }
    }
}
