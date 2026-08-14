import Foundation
import OSLog
import Supabase

private let log = Logger(subsystem: "com.tanman138.AndroidNotificationMirror", category: "Repository")

private let notificationsTable = "notifications"
private let notificationsChannel = "public:notifications"
private let statusFunction = "mirror_setup_status"

struct SetupStatus: Decodable {
    let inPublication: Bool
    let replicaIdentityFull: Bool
    let replyColumns: Bool

    enum CodingKeys: String, CodingKey {
        case inPublication = "in_publication"
        case replicaIdentityFull = "replica_identity_full"
        case replyColumns = "reply_columns"
    }

    init(inPublication: Bool, replicaIdentityFull: Bool, replyColumns: Bool) {
        self.inPublication = inPublication
        self.replicaIdentityFull = replicaIdentityFull
        self.replyColumns = replyColumns
    }

    // A project still running the two-column function decodes with false, which
    // reports setupOutdated — the fix is the same re-run either way.
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        inPublication = try container.decode(Bool.self, forKey: .inPublication)
        replicaIdentityFull = try container.decode(Bool.self, forKey: .replicaIdentityFull)
        replyColumns = try container.decodeIfPresent(Bool.self, forKey: .replyColumns) ?? false
    }
}

/// Anything the phone did that this Mac reacts to.
enum MirrorEvent {
    case incoming(MirroredNotification)
    case replyResult(ReplyResult)
}

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

    /// Emits every notification the phone inserts, and every reply outcome it
    /// writes back. One stream because a channel is cached by topic and every
    /// subscription must be declared before it joins.
    func mirrorEvents() -> AsyncStream<MirrorEvent> {
        AsyncStream { continuation in
            let task = Task {
                let channel = self.client.channel(notificationsChannel)
                let inserts = channel.postgresChange(
                    InsertAction.self,
                    schema: "public",
                    table: notificationsTable
                )
                let updates = channel.postgresChange(
                    UpdateAction.self,
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

                await withTaskGroup(of: Void.self) { group in
                    group.addTask {
                        for await insert in inserts {
                            do {
                                let row = try insert.record.decode(as: MirroredNotification.self)
                                continuation.yield(.incoming(row))
                            } catch {
                                log.error("Could not decode an inserted row: \(error.localizedDescription)")
                            }
                        }
                    }
                    group.addTask {
                        for await update in updates {
                            do {
                                let row = try update.record.decode(as: ReplyRow.self)
                                if let result = ReplyResult.from(row) {
                                    continuation.yield(.replyResult(result))
                                }
                            } catch {
                                log.error("Could not decode an updated row: \(error.localizedDescription)")
                            }
                        }
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
        } catch {
            return Self.classify(error)
        }
        // Reaching the table proves nothing about delivery: a table outside the
        // realtime publication reads fine and streams nothing.
        return await checkRealtimeReady()
    }

    private func checkRealtimeReady() async -> ConnectionCheck {
        do {
            let rows: [SetupStatus] = try await client.rpc(statusFunction).execute().value
            return Self.setupCheck(rows.first)
        } catch {
            // Missing function means the project was set up from older SQL.
            return .realtimeNotReady
        }
    }

    /// Turns a status row into a verdict. Nil means the function is missing.
    static func setupCheck(_ status: SetupStatus?) -> ConnectionCheck {
        guard let status, status.inPublication, status.replicaIdentityFull else {
            return .realtimeNotReady
        }
        return status.replyColumns ? .ok : .setupOutdated
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

    /// Hands a reply to the phone, which writes reply_status back as sent or
    /// failed — that arrives as a replyResult event.
    ///
    /// Addresses the row by primary key when one is known. Filtering on
    /// notification_key instead would match every message in the conversation —
    /// apps reuse one key for all of them — and the phone would then deliver the
    /// reply once per matched row.
    func sendReply(rowID: String?, notificationKey: String, text: String) async {
        do {
            let update = try client
                .from(notificationsTable)
                .update(["reply_text": text, "reply_status": "pending"])
            let filtered = rowID.map { update.eq("id", value: $0) }
                ?? update.eq("notification_key", value: notificationKey)
            try await filtered.execute()
            log.debug("Sent a reply for \(notificationKey, privacy: .public).")
        } catch {
            log.error("Could not send the reply: \(error.localizedDescription)")
        }
    }
}
