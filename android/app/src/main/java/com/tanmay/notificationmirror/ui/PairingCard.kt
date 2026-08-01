package com.tanmay.notificationmirror.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
fun PairingCard(
    pairingString: String?,
    onGenerate: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Pair your Mac", style = MaterialTheme.typography.titleMedium)
            Text(
                "Generate a code and paste it into the Mac app. It expires in five minutes. " +
                    "Anyone with this code can read your mirrored notifications, so do not share it.",
                style = MaterialTheme.typography.bodyMedium
            )
            if (pairingString != null) {
                Text(
                    pairingString,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
                OutlinedButton(onClick = onCopy) { Text("Copy code") }
            }
            OutlinedButton(onClick = onGenerate) {
                Text(if (pairingString == null) "Generate pairing code" else "Regenerate")
            }
        }
    }
}
