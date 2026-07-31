package com.tanmay.notificationmirror.data

import com.tanmay.notificationmirror.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val URL_KEY = "supabase_url"
private const val KEY_KEY = "supabase_key"

/** Stored configuration wins; build-time values keep local development working. */
fun resolveConfig(stored: SupabaseConfig?, buildTime: SupabaseConfig?): SupabaseConfig? =
    stored ?: buildTime

class ConfigStore(private val secureStore: SecureStore) {

    private val state = MutableStateFlow(resolveConfig(readStored(), buildTimeConfig()))
    val configChanges: StateFlow<SupabaseConfig?> = state.asStateFlow()

    fun current(): SupabaseConfig? = state.value

    fun save(config: SupabaseConfig) {
        secureStore.put(URL_KEY, config.url)
        secureStore.put(KEY_KEY, config.publishableKey)
        state.value = config
    }

    fun clear() {
        secureStore.remove(URL_KEY)
        secureStore.remove(KEY_KEY)
        state.value = buildTimeConfig()
    }

    private fun readStored(): SupabaseConfig? {
        val url = secureStore.get(URL_KEY) ?: return null
        val key = secureStore.get(KEY_KEY) ?: return null
        return parseSupabaseConfig(url, key).getOrNull()
    }

    private fun buildTimeConfig(): SupabaseConfig? =
        parseSupabaseConfig(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY).getOrNull()
}
