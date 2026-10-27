package com.adspro.app.ui
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val BrandGreen = Color(0xFF16A34A)

@Composable fun GreenNextButton(text: String = "Continue", onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(24.dp).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)) { Text(text, color = Color.White, fontSize = 18.sp) }
}
@Composable fun PermissionRow(title: String, subtitle: String, granted: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(subtitle, style = MaterialTheme.typography.bodySmall) }
            Icon(imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, contentDescription = null, tint = if (granted) BrandGreen else MaterialTheme.colorScheme.outline)
        }
    }
}
@Composable fun ServiceStatusCard(running: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = if (running) Icons.Filled.CheckCircle else Icons.Filled.Warning, contentDescription = null, tint = if (running) BrandGreen else Color(0xFFEAB308))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(if (running) "Always on" else "Restarting…", style = MaterialTheme.typography.titleMedium); Text(if (running) "Ads Pro forwards SMS even when the app is closed" else "Watchdog will restart Ads Pro within 15 min", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
@Composable fun PlaceholderPage(title: String, onNext: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { Text(title, style = MaterialTheme.typography.headlineMedium); Text("Coming soon."); Spacer(Modifier.weight(1f)); GreenNextButton(onClick = onNext) }
}
