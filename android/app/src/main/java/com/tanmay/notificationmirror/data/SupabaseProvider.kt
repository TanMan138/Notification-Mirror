package com.tanmay.notificationmirror.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

object SupabaseProvider {

    fun create(config: SupabaseConfig): SupabaseClient = createSupabaseClient(
        supabaseUrl = config.url,
        supabaseKey = config.publishableKey
    ) {
        install(Postgrest)
        install(Realtime)
    }
}
