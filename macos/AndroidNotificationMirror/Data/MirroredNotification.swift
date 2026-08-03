import Foundation

/// A row of the `notifications` table, as written by the phone.
struct MirroredNotification: Decodable {
    let notificationKey: String
    let appName: String?
    let title: String
    let bodyText: String?
    let base64Icon: String?
    let isRepliable: Bool

    enum CodingKeys: String, CodingKey {
        case notificationKey = "notification_key"
        case appName = "app_name"
        case title
        case bodyText = "body_text"
        case base64Icon = "base64_icon"
        case isRepliable = "is_repliable"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        notificationKey = try container.decode(String.self, forKey: .notificationKey)
        appName = try container.decodeIfPresent(String.self, forKey: .appName)
        title = try container.decode(String.self, forKey: .title)
        bodyText = try container.decodeIfPresent(String.self, forKey: .bodyText)
        base64Icon = try container.decodeIfPresent(String.self, forKey: .base64Icon)
        // A phone still on the previous release omits the column entirely.
        isRepliable = try container.decodeIfPresent(Bool.self, forKey: .isRepliable) ?? false
    }
}
