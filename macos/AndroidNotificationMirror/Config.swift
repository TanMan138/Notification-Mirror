import Foundation

/// Supabase configuration from Info.plist (injected at build time from Secrets.xcconfig).
/// We use SUPABASE_HOST (not full URL) because xcconfig treats "//" as comment.
enum Config {
    static var supabaseURL: String {
        let host = Bundle.main.object(forInfoDictionaryKey: "SUPABASE_HOST") as? String ?? ""
        guard !host.isEmpty else { return "" }
        return "https://\(host)"
    }

    static var supabasePublishableKey: String {
        Bundle.main.object(forInfoDictionaryKey: "SUPABASE_PUBLISHABLE_KEY") as? String ?? ""
    }
}
