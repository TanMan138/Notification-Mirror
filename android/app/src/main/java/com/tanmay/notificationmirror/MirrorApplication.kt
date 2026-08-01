package com.tanmay.notificationmirror

import android.app.Application
import android.content.Context
import com.tanmay.notificationmirror.data.ConfigStore
import com.tanmay.notificationmirror.data.NotificationRepository
import com.tanmay.notificationmirror.data.RetentionSweeper
import com.tanmay.notificationmirror.data.SecureStore
import com.tanmay.notificationmirror.data.SupabaseNotificationRepository
import com.tanmay.notificationmirror.data.SupabaseProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Owns the config store and the Supabase client built from it. */
class MirrorApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val secureStore: SecureStore by lazy { SecureStore(this) }

    val configStore: ConfigStore by lazy { ConfigStore(secureStore) }

    /** Null while the app is unconfigured. Rebuilt whenever configuration changes. */
    val repositoryFlow: StateFlow<NotificationRepository?> by lazy {
        configStore.configChanges
            .map { config -> config?.let { SupabaseNotificationRepository(SupabaseProvider.create(it)) } }
            .stateIn(
                scope = appScope,
                started = SharingStarted.Eagerly,
                initialValue = configStore.current()
                    ?.let { SupabaseNotificationRepository(SupabaseProvider.create(it)) }
            )
    }

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            val repository = repositoryFlow.value ?: return@launch
            RetentionSweeper(secureStore).sweepIfDue(repository, System.currentTimeMillis() / 1000)
        }
    }
}

val Context.mirrorApp: MirrorApplication
    get() = applicationContext as MirrorApplication
