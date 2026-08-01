package com.tanmay.notificationmirror.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/** Stateless: the activity owns the fields and does the parsing and probing. */
@Composable
fun SetupScreen(
    url: String,
    key: String,
    sql: String,
    status: String?,
    isChecking: Boolean,
    onUrlChange: (String) -> Unit,
    onKeyChange: (String) -> Unit,
    onCopySql: () -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("1. Create a project", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Create a free project at supabase.com, open the SQL editor, and run this:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    sql,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
                OutlinedButton(onClick = onCopySql) { Text("Copy SQL") }
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("2. Connect", style = MaterialTheme.typography.titleMedium)
                Text(
                    "From Project Settings, API: copy the project URL and the publishable key. " +
                        "Do not use the service role key.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = onUrlChange,
                    label = { Text("Project URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = onKeyChange,
                    label = { Text("Publishable key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = onConnect,
                    enabled = !isChecking,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isChecking) "Checking…" else "Test connection")
                }
                if (status != null) {
                    Text(status, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
