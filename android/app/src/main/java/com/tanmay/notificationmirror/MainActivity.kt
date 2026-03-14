package com.tanmay.notificationmirror

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.tanmay.notificationmirror.ui.theme.NotificationMirrorTheme

private const val TEST_NOTIFICATION_CHANNEL_ID = "notification_mirror_test"
private const val TEST_NOTIFICATION_ID = 1

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private var hasNotificationAccess by mutableStateOf(false)
    private var isIgnoringBatteryOptimizations by mutableStateOf(true)

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) postTestNotification(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hasNotificationAccess = checkNotificationAccess(this)
        isIgnoringBatteryOptimizations = checkBatteryOptimizationIgnored(this)
        setContent {
            NotificationMirrorTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text("Notification Mirror") }
                        )
                    }
                ) { innerPadding ->
                    NotificationMirrorScreen(
                        hasNotificationAccess = hasNotificationAccess,
                        isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                        onFixPermissions = { handleFixPermissions() },
                        onSendTestNotification = { trySendTestNotification() },
                        modifier = Modifier
                            .padding(innerPadding)
                            .padding(16.dp)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasNotificationAccess = checkNotificationAccess(this)
        isIgnoringBatteryOptimizations = checkBatteryOptimizationIgnored(this)
    }

    private fun handleFixPermissions() {
        when {
            !hasNotificationAccess -> startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            !isIgnoringBatteryOptimizations -> {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }

    private fun trySendTestNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationManagerCompat.from(this).areNotificationsEnabled()
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            postTestNotification(this)
        }
    }

}

private fun checkNotificationAccess(context: Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false
    val packageName = context.packageName
    return enabled.split(":").any { component ->
        component.trim().startsWith("$packageName/")
    }
}

private fun checkBatteryOptimizationIgnored(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

@Composable
private fun NotificationMirrorScreen(
    hasNotificationAccess: Boolean,
    isIgnoringBatteryOptimizations: Boolean,
    onFixPermissions: () -> Unit,
    onSendTestNotification: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Permissions",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (hasNotificationAccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (hasNotificationAccess) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = if (hasNotificationAccess) "Notification access enabled" else "Notification access disabled",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (isIgnoringBatteryOptimizations) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (isIgnoringBatteryOptimizations) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = if (isIgnoringBatteryOptimizations) "Battery optimization disabled" else "Battery optimization may limit mirroring",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (!hasNotificationAccess || !isIgnoringBatteryOptimizations) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onFixPermissions,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Fix permissions")
                    }
                }
            }
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Testing",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onSendTestNotification,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Send test notification")
                }
            }
        }
    }
}

private fun ensureTestNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val channel = NotificationChannel(
        TEST_NOTIFICATION_CHANNEL_ID,
        "Test notifications",
        NotificationManager.IMPORTANCE_DEFAULT
    )
    context.getSystemService<NotificationManager>()?.createNotificationChannel(channel)
}

private fun postTestNotification(context: Context) {
    ensureTestNotificationChannel(context)
    val notification = NotificationCompat.Builder(context, TEST_NOTIFICATION_CHANNEL_ID)
        .setContentTitle("Test notification")
        .setContentText("This was sent from Notification Mirror to test the connection.")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()
    NotificationManagerCompat.from(context).notify(TEST_NOTIFICATION_ID, notification)
}
