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

    @Test func decodesAUuidPrimaryKey() throws {
        // Projects created before the documented schema use gen_random_uuid().
        let notification = try decode("""
        {"id": "9efe35fb-8bff-4fb3-87bc-92c71ef4b74e", "notification_key": "k",
         "app_name": "Signal", "title": "Ada", "body_text": null, "base64_icon": null}
        """)

        #expect(notification.id == "9efe35fb-8bff-4fb3-87bc-92c71ef4b74e")
    }

    @Test func decodesABigintPrimaryKey() throws {
        // What the documented setup SQL creates: bigint generated always as identity.
        let notification = try decode("""
        {"id": 42, "notification_key": "k", "app_name": "Signal",
         "title": "Ada", "body_text": null, "base64_icon": null}
        """)

        #expect(notification.id == "42")
    }

    @Test func survivesARowWithNoIdAtAll() throws {
        let notification = try decode("""
        {"notification_key": "k", "app_name": "Signal", "title": "Ada",
         "body_text": null, "base64_icon": null}
        """)

        #expect(notification.id == nil)
    }

    @Test func decodesTheRepliableFlag() throws {
        let notification = try decode("""
        {"notification_key": "k", "app_name": "Signal", "title": "Ada",
         "body_text": "dinner?", "base64_icon": null, "is_repliable": true}
        """)

        #expect(notification.isRepliable)
    }

    @Test func treatsAMissingRepliableFlagAsNotRepliable() throws {
        // A phone still on the previous release omits the column entirely.
        let notification = try decode("""
        {"notification_key": "k", "app_name": "Signal", "title": "Ada",
         "body_text": null, "base64_icon": null}
        """)

        #expect(!notification.isRepliable)
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
