import Foundation

/// The reply columns of a row, as they arrive on a realtime UPDATE.
struct ReplyRow: Decodable {
    let notificationKey: String
    let appName: String?
    let replyText: String?
    let replyStatus: String?

    enum CodingKeys: String, CodingKey {
        case notificationKey = "notification_key"
        case appName = "app_name"
        case replyText = "reply_text"
        case replyStatus = "reply_status"
    }
}

/// The phone's verdict on a reply this Mac sent.
struct ReplyResult {
    let notificationKey: String
    let appName: String?
    let text: String
    let delivered: Bool

    /// Nil for anything that is not an outcome — including this Mac's own
    /// 'pending' write coming back around.
    static func from(_ row: ReplyRow) -> ReplyResult? {
        guard let status = row.replyStatus, status == "sent" || status == "failed" else { return nil }
        guard let text = row.replyText else { return nil }
        return ReplyResult(
            notificationKey: row.notificationKey,
            appName: row.appName,
            text: text,
            delivered: status == "sent"
        )
    }
}
