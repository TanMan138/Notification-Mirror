import Foundation
import Supabase

enum SupabaseProvider {

    /// True when Secrets.xcconfig supplied both values at build time.
    static var isConfigured: Bool {
        !Config.supabaseURL.isEmpty && !Config.supabasePublishableKey.isEmpty
    }

    static func makeClient() -> SupabaseClient? {
        guard isConfigured, let url = URL(string: Config.supabaseURL) else { return nil }
        return SupabaseClient(
            supabaseURL: url,
            supabaseKey: Config.supabasePublishableKey,
            options: SupabaseClientOptions(
                auth: SupabaseClientOptions.AuthOptions(
                    emitLocalSessionAsInitialSession: true
                )
            )
        )
    }
}
