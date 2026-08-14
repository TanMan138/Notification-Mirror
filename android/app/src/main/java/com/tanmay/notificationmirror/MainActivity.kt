package com.tanmay.notificationmirror

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import com.tanmay.notificationmirror.data.ConfigError
import com.tanmay.notificationmirror.data.ConnectionCheck
import com.tanmay.notificationmirror.data.SupabaseConfig
import com.tanmay.notificationmirror.data.SupabaseNotificationRepository
import com.tanmay.notificationmirror.data.SupabaseProvider
import com.tanmay.notificationmirror.data.encodePairingPayload
import com.tanmay.notificationmirror.data.parseSupabaseConfig
import com.tanmay.notificationmirror.ui.PairingCard
import com.tanmay.notificationmirror.notifications.TEST_NOTIFICATION_CHANNEL_ID
import com.tanmay.notificationmirror.notifications.postRepliableTestNotification
import com.tanmay.notificationmirror.ui.SetupScreen
import com.tanmay.notificationmirror.ui.theme.NotificationMirrorTheme
import kotlinx.coroutines.launch

private const val TEST_NOTIFICATION_ID = 1

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private var hasNotificationAccess by mutableStateOf(false)
    private var isIgnoringBatteryOptimizations by mutableStateOf(true)

    private var config by mutableStateOf<SupabaseConfig?>(null)
    private var urlField by mutableStateOf("")
    private var keyField by mutableStateOf("")
    private var setupStatus by mutableStateOf<String?>(null)
    private var isChecking by mutableStateOf(false)
    private var pairingString by mutableStateOf<String?>(null)
    private var isSetupOutdated by mutableStateOf(false)

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) postTestNotification(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hasNotificationAccess = checkNotificationAccess(this)
        isIgnoringBatteryOptimizations = checkBatteryOptimizationIgnored(this)
        config = mirrorApp.configStore.current()
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
                    val contentModifier = Modifier
                        .padding(innerPadding)
                        .padding(16.dp)

                    if (config == null) {
                        SetupScreen(
                            url = urlField,
                            key = keyField,
                            sql = getString(R.string.setup_sql),
                            status = setupStatus,
                            isChecking = isChecking,
                            onUrlChange = { urlField = it },
                            onKeyChange = { keyField = it },
                            onCopySql = { copyToClipboard("SQL", getString(R.string.setup_sql)) },
                            onConnect = { connect() },
                            modifier = contentModifier
                        )
                    } else {
                        NotificationMirrorScreen(
                            hasNotificationAccess = hasNotificationAccess,
                            isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                            onFixPermissions = { handleFixPermissions() },
                            onSendTestNotification = { trySendTestNotification() },
                            onSendRepliableTestNotification = { trySendRepliableTestNotification() },
                            isSetupOutdated = isSetupOutdated,
                            onCopySql = { copyToClipboard("SQL", getString(R.string.setup_sql)) },
                            pairing = {
                                PairingCard(
                                    pairingString = pairingString,
                                    onGenerate = { generatePairingCode() },
                                    onCopy = { pairingString?.let { copyToClipboard("Pairing code", it) } }
                                )
                            },
                            modifier = contentModifier
                        )
                    }
                }
            }
        }
    }

    /** Parses, probes the project, and only stores credentials that actually worked. */
    private fun connect() {
        val parsed = parseSupabaseConfig(urlField, keyField).getOrElse { error ->
            setupStatus = configErrorMessage(error)
            return
        }

        isChecking = true
        setupStatus = null
        lifecycleScope.launch {
            val client = SupabaseProvider.create(parsed)
            val result = try {
                SupabaseNotificationRepository(client).checkConnection()
            } finally {
                client.close()
            }
            setupStatus = result.userMessage
            if (result is ConnectionCheck.Ok) {
                mirrorApp.configStore.save(parsed)
                config = parsed
            }
            isChecking = false
        }
    }

    private fun generatePairingCode() {
        val current = config ?: return
        pairingString = encodePairingPayload(current, System.currentTimeMillis() / 1000)
    }

    private fun copyToClipboard(label: String, text: String) {
        getSystemService<ClipboardManager>()?.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    override fun onResume() {
        super.onResume()
        hasNotificationAccess = checkNotificationAccess(this)
        isIgnoringBatteryOptimizations = checkBatteryOptimizationIgnored(this)
        checkSchemaIsCurrent()
    }

    /**
     * A project set up before replies existed keeps mirroring happily and
     * silently drops every reply. The setup screen reports it, but a configured
     * user never sees that screen, so the main screen has to say so too.
     */
    private fun checkSchemaIsCurrent() {
        val repository = mirrorApp.repositoryFlow.value ?: return
        lifecycleScope.launch {
            // Only this one verdict is actionable here. A network failure means
            // we simply do not know, and nagging about it would be noise.
            isSetupOutdated = repository.checkConnection() is ConnectionCheck.SetupOutdated
        }
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

    /** Stands in for a messaging app, so the Mac round trip needs no second person. */
    private fun trySendRepliableTestNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationManagerCompat.from(this).areNotificationsEnabled()
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            ensureTestNotificationChannel(this)
            postRepliableTestNotification(this)
        }
    }

}

private fun configErrorMessage(error: Throwable): String = when (error) {
    ConfigError.UrlBlank -> "Enter your project URL."
    ConfigError.UrlNotHttps -> "The URL must start with https://"
    ConfigError.UrlNotSupabase -> "That does not look like a Supabase project URL."
    ConfigError.KeyBlank -> "Enter your publishable key."
    ConfigError.KeyIsSecret ->
        "That is a secret key. Use the publishable key instead — a secret key would give " +
            "anyone with this app full access to your database."
    else -> "That configuration could not be read."
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
    onSendRepliableTestNotification: () -> Unit,
    isSetupOutdated: Boolean,
    onCopySql: () -> Unit,
    pairing: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (isSetupOutdated) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "Replies are not set up",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your Supabase project was created before replies were supported. " +
                            "Notifications still mirror, but replying from the Mac does nothing. " +
                            "Run the setup SQL again to fix it — it is safe to re-run.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onCopySql,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Copy SQL")
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
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onSendRepliableTestNotification,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Send repliable test notification")
                }
            }
        }

        pairing()
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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    ensureTestNotificationChannel(context)
    val notification = NotificationCompat.Builder(context, TEST_NOTIFICATION_CHANNEL_ID)
        .setContentTitle("Test notification")
        .setContentText("This was sent from Notification Mirror to test the connection.")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()
    NotificationManagerCompat.from(context).notify(TEST_NOTIFICATION_ID, notification)
}
