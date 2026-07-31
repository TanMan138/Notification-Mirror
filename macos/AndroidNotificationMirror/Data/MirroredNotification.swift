import Foundation

/// A row of the `notifications` table, as written by the phone.
struct MirroredNotification: Decodable {
    let notificationKey: String
    let appName: String?
    let title: String
    let bodyText: String?
    let base64Icon: String?

    enum CodingKeys: String, CodingKey {
        case notificationKey = "notification_key"
        case appName = "app_name"
        case title
        case bodyText = "body_text"
        case base64Icon = "base64_icon"
    }
}
