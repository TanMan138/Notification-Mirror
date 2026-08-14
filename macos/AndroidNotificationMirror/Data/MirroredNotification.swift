import Foundation

/// A row of the `notifications` table, as written by the phone.
struct MirroredNotification: Decodable {
    /// The row's primary key, as a string. Projects created from the documented
    /// SQL use a bigint; older ones use gen_random_uuid(). Replies address this
    /// rather than notification_key, which apps reuse across every message in a
    /// conversation — filtering on the key updates the whole history at once.
    let id: String?
    let notificationKey: String
    let appName: String?
    let title: String
    let bodyText: String?
    let base64Icon: String?
    let isRepliable: Bool

    enum CodingKeys: String, CodingKey {
        case id
        case notificationKey = "notification_key"
        case appName = "app_name"
        case title
        case bodyText = "body_text"
        case base64Icon = "base64_icon"
        case isRepliable = "is_repliable"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        // uuid arrives as a string, bigint as a number. Both end up as text in
        // the filter, which PostgREST accepts either way.
        if let text = try? container.decodeIfPresent(String.self, forKey: .id) {
            id = text
        } else if let number = try? container.decodeIfPresent(Int64.self, forKey: .id) {
            id = String(number)
        } else {
            id = nil
        }
        notificationKey = try container.decode(String.self, forKey: .notificationKey)
        appName = try container.decodeIfPresent(String.self, forKey: .appName)
        title = try container.decode(String.self, forKey: .title)
        bodyText = try container.decodeIfPresent(String.self, forKey: .bodyText)
        base64Icon = try container.decodeIfPresent(String.self, forKey: .base64Icon)
        // A phone still on the previous release omits the column entirely.
        isRepliable = try container.decodeIfPresent(Bool.self, forKey: .isRepliable) ?? false
    }
}
