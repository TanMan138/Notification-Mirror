package com.tanmay.notificationmirror

import android.app.Application
import android.content.Context
import com.tanmay.notificationmirror.data.ConfigStore
import com.tanmay.notificationmirror.data.NotificationRepository
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

/** Owns the config store and the Supabase client built from it. */
class MirrorApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val configStore: ConfigStore by lazy { ConfigStore(SecureStore(this)) }

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
}

val Context.mirrorApp: MirrorApplication
    get() = applicationContext as MirrorApplication
