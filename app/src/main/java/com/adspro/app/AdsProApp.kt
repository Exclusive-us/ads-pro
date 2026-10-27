package com.adspro.app
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.adspro.app.data.AppDatabase
import java.util.UUID

class AdsProApp : Application(), ImageLoaderFactory {
    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val deviceId: String by lazy {
        val prefs = getSharedPreferences("adspro", Context.MODE_PRIVATE)
        prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("device_id", it).apply() }
    }
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Ads Pro Service", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false); enableVibration(false); setSound(null, null)
            })
        }
    }
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this).components { add(VideoFrameDecoder.Factory()) }.build()
    companion object {
        const val SUPABASE_URL = "https://bqddohfszkqpvncpbrun.supabase.co"
        const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJxZGRvaGZzemtxcHZuY3BicnVuIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTI2MDAwNjIsImV4cCI6MjEwODE3NjA2Mn0.J0uq4JPpPxoU0hc4ry5WglCV_HcTqshcHsl3oCJ7upA"
        const val REST_URL = "$SUPABASE_URL/rest/v1"
        const val STORAGE_URL = "$SUPABASE_URL/storage/v1"
        const val CHANNEL_ID = "ads_pro_service"
        const val NOTIFICATION_ID = 1001
    }
}
