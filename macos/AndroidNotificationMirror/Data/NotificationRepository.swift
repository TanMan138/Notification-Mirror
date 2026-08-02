import Foundation
import OSLog
import Supabase

private let log = Logger(subsystem: "com.tanman138.AndroidNotificationMirror", category: "Repository")

private let notificationsTable = "notifications"
private let notificationsChannel = "public:notifications"

enum SocketStatus {
    case connected
    case disconnected
}

/// The only type that talks to Supabase.
final class NotificationRepository {

    private let client: SupabaseClient

    init(client: SupabaseClient) {
        self.client = client
    }

    /// Emits every notification the phone inserts.
    func incomingNotifications() -> AsyncStream<MirroredNotification> {
        AsyncStream { continuation in
            let task = Task {
                let channel = self.client.channel(notificationsChannel)
                let inserts = channel.postgresChange(
                    InsertAction.self,
                    schema: "public",
                    table: notificationsTable
                )

                do {
                    try await channel.subscribeWithError()
                } catch {
                    log.error("Could not subscribe to notifications: \(error.localizedDescription)")
                    continuation.finish()
                    return
                }

                for await insert in inserts {
                    do {
                        continuation.yield(try insert.record.decode(as: MirroredNotification.self))
                    } catch {
                        log.error("Could not decode an inserted row: \(error.localizedDescription)")
                    }
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    /// Emits the realtime socket's connection state.
    func socketStatus() -> AsyncStream<SocketStatus> {
        AsyncStream { continuation in
            let task = Task {
                for await status in self.client.realtimeV2.statusChange {
                    continuation.yield(status == .connected ? .connected : .disconnected)
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    /// Probes the project during setup. Never throws.
    func checkConnection() async -> ConnectionCheck {
        do {
            _ = try await client.from(notificationsTable).select().limit(1).execute()
            return .ok
        } catch {
            return Self.classify(error)
        }
    }

    /// Maps a probe failure onto something a user can act on. Confirmed against
    /// real supabase-swift failures:
    ///  - PostgrestError "Invalid API key"
    ///  - NSURLError "A server with the specified hostname could not be found."
    /// The table case reuses the server's own wording, which the Android client
    /// recorded as "Could not find the table '...' in the schema cache".
    static func classify(_ error: Error) -> ConnectionCheck {
        let message = error.localizedDescription.lowercased()
        if message.contains("pgrst205") || message.contains("could not find the table") {
            return .tableMissing
        }
        if message.contains("401") || message.contains("403")
            || message.contains("invalid api key") || message.contains("jwt") {
            return .invalidCredentials
        }
        if (error as NSError).domain == NSURLErrorDomain {
            return .unreachable
        }
        return .unknown(Self.safeDetail(error))
    }

    /// A failure message can carry the request headers, and therefore the API
    /// key. Only the first line is safe to show.
    static func safeDetail(_ error: Error) -> String {
        let first = error.localizedDescription
            .split(separator: "\n", maxSplits: 1)
            .first?
            .trimmingCharacters(in: .whitespaces) ?? ""
        return first.isEmpty ? "\(type(of: error))" : first
    }

    func markDismissed(notificationKey: String) async {
        do {
            try await client
                .from(notificationsTable)
                .update(["is_dismissed": true])
                .eq("notification_key", value: notificationKey)
                .execute()
            log.debug("Marked \(notificationKey, privacy: .public) dismissed.")
        } catch {
            log.error("Could not mark dismissed: \(error.localizedDescription)")
        }
    }
}
