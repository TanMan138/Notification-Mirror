import Foundation

private let urlAccount = "supabase_url"
private let keyAccount = "supabase_key"

/// Stored configuration wins; Info.plist values keep local development working.
@MainActor
@Observable
final class ConfigStore {

    private let keychain = KeychainStore()
    private var continuations: [UUID: AsyncStream<SupabaseConfig?>.Continuation] = [:]

    private(set) var current: SupabaseConfig?

    init() {
        current = Self.stored(keychain) ?? Self.buildTime()
    }

    func save(_ config: SupabaseConfig) {
        keychain.set(config.url, for: urlAccount)
        keychain.set(config.publishableKey, for: keyAccount)
        current = config
        broadcast()
    }

    func clear() {
        keychain.remove(urlAccount)
        keychain.remove(keyAccount)
        current = Self.buildTime()
        broadcast()
    }

    func configChanges() -> AsyncStream<SupabaseConfig?> {
        AsyncStream { continuation in
            let id = UUID()
            continuations[id] = continuation
            continuation.yield(current)
            continuation.onTermination = { [weak self] _ in
                Task { @MainActor in self?.continuations[id] = nil }
            }
        }
    }

    private func broadcast() {
        for continuation in continuations.values { continuation.yield(current) }
    }

    private static func stored(_ keychain: KeychainStore) -> SupabaseConfig? {
        guard let url = keychain.get(urlAccount), let key = keychain.get(keyAccount) else { return nil }
        return try? SupabaseConfig.parse(url: url, key: key).get()
    }

    private static func buildTime() -> SupabaseConfig? {
        try? SupabaseConfig.parse(url: Config.supabaseURL, key: Config.supabasePublishableKey).get()
    }
}
