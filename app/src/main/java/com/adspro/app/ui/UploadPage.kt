package com.adspro.app.ui
import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.adspro.app.AdsProApp
import com.adspro.app.net.Supabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable fun UploadPage(onNext: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope(); val app = ctx.applicationContext as AdsProApp
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) { uris.forEach { uri -> runCatching { uploadOne(ctx, app.deviceId, uri) } } }
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Upload media", style = MaterialTheme.typography.headlineMedium)
        Text("Photos and videos you select are uploaded to your Supabase bucket and appear on the next page.")
        Button(onClick = { if (Build.VERSION.SDK_INT >= 33) permLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)) else permLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)) }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Choose photos & videos") }
        Spacer(Modifier.weight(1f)); GreenNextButton(onClick = onNext)
    }
}
private suspend fun uploadOne(ctx: Context, deviceId: String, uri: Uri) = withContext(Dispatchers.IO) {
    val mime = ctx.contentResolver.getType(uri) ?: "application/octet-stream"
    val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
    val tmp = File.createTempFile("upload_", ".$ext", ctx.cacheDir)
    try {
        ctx.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } } ?: return@withContext
        Supabase.uploadFile("$deviceId/${System.currentTimeMillis()}_${UUID.randomUUID()}.$ext", tmp, mime)
    } finally { tmp.delete() }
}
