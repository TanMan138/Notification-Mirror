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
