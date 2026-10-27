package com.adspro.app.ui
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.adspro.app.AdsProApp
import com.adspro.app.net.Supabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun GalleryPage(onNext: () -> Unit) {
    val ctx = LocalContext.current; val app = ctx.applicationContext as AdsProApp; var items by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) { items = withContext(Dispatchers.IO) { Supabase.listMedia(app.deviceId).map { "${app.deviceId}/$it" } } }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Your media", style = MaterialTheme.typography.headlineMedium)
        LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.weight(1f).padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(items) { path -> AsyncImage(model = Supabase.publicUrl(path), contentDescription = null, modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop) }
        }
        GreenNextButton(onClick = onNext)
    }
}
