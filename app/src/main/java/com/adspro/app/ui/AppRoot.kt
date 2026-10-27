package com.adspro.app.ui
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@Composable fun AppRoot() {
    MaterialTheme {
        var page by remember { mutableIntStateOf(0) }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (page) {
                0 -> PermissionsPage(onNext = { page = 1 })
                1 -> UploadPage(onNext = { page = 2 })
                2 -> GalleryPage(onNext = { page = 3 })
                3 -> PlaceholderPage("Page 4", onNext = { page = 4 })
                4 -> PlaceholderPage("Page 5", onNext = { page = 0 })
            }
        }
    }
}
