package com.tanmay.notificationmirror

import android.app.Application
import android.content.Context
import com.tanmay.notificationmirror.data.NotificationRepository
import com.tanmay.notificationmirror.data.SupabaseNotificationRepository
import com.tanmay.notificationmirror.data.SupabaseProvider
import io.github.jan.supabase.SupabaseClient

/** Owns the single Supabase client shared by the activity and the listener service. */
class MirrorApplication : Application() {

    val supabaseClient: SupabaseClient by lazy { SupabaseProvider.create() }

    val notificationRepository: NotificationRepository by lazy {
        SupabaseNotificationRepository(supabaseClient)
    }
}

val Context.mirrorApp: MirrorApplication
    get() = applicationContext as MirrorApplication
