import Foundation

/// Credentials for the user's own Supabase project.
struct SupabaseConfig: Equatable {
    let url: String
    let publishableKey: String
}

enum ConfigError: Error, Equatable {
    case urlBlank
    case urlNotHTTPS
    case urlNotSupabase
    case keyBlank

    /// A service-role key bypasses row level security and must never ship in a client.
    case keyIsSecret
}

private let supabaseHostSuffix = ".supabase.co"

extension SupabaseConfig {

    static func parse(url: String, key: String) -> Result<SupabaseConfig, ConfigError> {
        var cleanURL = url.trimmingCharacters(in: .whitespacesAndNewlines)
        while cleanURL.hasSuffix("/") { cleanURL.removeLast() }
        let cleanKey = key.trimmingCharacters(in: .whitespacesAndNewlines)

        guard !cleanURL.isEmpty else { return .failure(.urlBlank) }
        guard cleanURL.hasPrefix("https://") else { return .failure(.urlNotHTTPS) }

        let host = cleanURL.dropFirst("https://".count).prefix { $0 != "/" }
        guard host.hasSuffix(supabaseHostSuffix) else { return .failure(.urlNotSupabase) }

        guard !cleanKey.isEmpty else { return .failure(.keyBlank) }
        guard !isSecretKey(cleanKey) else { return .failure(.keyIsSecret) }

        return .success(SupabaseConfig(url: cleanURL, publishableKey: cleanKey))
    }

    /// Catches the common mistake of pasting the service-role key from the dashboard.
    static func isSecretKey(_ key: String) -> Bool {
        if key.hasPrefix("sb_secret_") { return true }
        return jwtRole(key) == "service_role"
    }

    // Reads the top-level claim only. The Android parser must agree exactly, or a key
    // rejected on one platform would be accepted on the other. Empty segments are kept
    // so the segment count matches Kotlin's split.
    private static func jwtRole(_ token: String) -> String? {
        let parts = token.split(separator: ".", omittingEmptySubsequences: false)
        guard parts.count == 3 else { return nil }

        var base64 = String(parts[1])
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        while base64.count % 4 != 0 { base64 += "=" }

        guard let data = Data(base64Encoded: base64),
              let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return nil
        }
        return object["role"] as? String
    }
}
