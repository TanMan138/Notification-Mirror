import Foundation
import Supabase

enum SupabaseProvider {

    static func makeClient(_ config: SupabaseConfig) -> SupabaseClient? {
        guard let url = URL(string: config.url) else { return nil }
        return SupabaseClient(
            supabaseURL: url,
            supabaseKey: config.publishableKey,
            options: SupabaseClientOptions(
                auth: SupabaseClientOptions.AuthOptions(
                    emitLocalSessionAsInitialSession: true
                )
            )
        )
    }
}
