import Foundation
import Testing
@testable import AndroidNotificationMirror

@Suite struct MirroredNotificationTests {

    private func decode(_ json: String) throws -> MirroredNotification {
        try JSONDecoder().decode(MirroredNotification.self, from: Data(json.utf8))
    }

    @Test func decodesAFullRow() throws {
        let notification = try decode("""
        {
          "notification_key": "0|com.example|1|null|10123",
          "app_name": "Example",
          "title": "Hello",
          "body_text": "World",
          "base64_icon": "AAAA"
        }
        """)

        #expect(notification.notificationKey == "0|com.example|1|null|10123")
        #expect(notification.appName == "Example")
        #expect(notification.title == "Hello")
        #expect(notification.bodyText == "World")
        #expect(notification.base64Icon == "AAAA")
    }

    @Test func decodesARowWithNoAppName() throws {
        let notification = try decode("""
        {"notification_key": "k", "app_name": null, "title": "Hello",
         "body_text": "World", "base64_icon": null}
        """)

        #expect(notification.appName == nil)
        #expect(notification.base64Icon == nil)
    }

    @Test func decodesARowWithNoBody() throws {
        let notification = try decode("""
        {"notification_key": "k", "app_name": "Example", "title": "Hello",
         "body_text": null, "base64_icon": null}
        """)

        #expect(notification.bodyText == nil)
    }

    @Test func decodesARowWithExtraColumns() throws {
        let notification = try decode("""
        {"id": 42, "notification_key": "k", "app_name": "Example",
         "title": "Hello", "body_text": "World", "base64_icon": null,
         "is_dismissed": false, "created_at": "2026-07-31T00:00:00Z"}
        """)

        #expect(notification.title == "Hello")
    }
}
