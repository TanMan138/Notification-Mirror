import Foundation
import Testing
@testable import AndroidNotificationMirror

@Suite struct ReplyResultTests {

    private func decode(_ json: String) throws -> ReplyRow {
        try JSONDecoder().decode(ReplyRow.self, from: Data(json.utf8))
    }

    private func row(status: String?, text: String? = "on my way") -> ReplyRow {
        ReplyRow(notificationKey: "key", appName: "Signal", replyText: text, replyStatus: status)
    }

    @Test func reportsADeliveredReply() {
        let result = ReplyResult.from(row(status: "sent"))

        #expect(result?.delivered == true)
        #expect(result?.text == "on my way")
        #expect(result?.notificationKey == "key")
    }

    @Test func reportsAFailedReply() {
        #expect(ReplyResult.from(row(status: "failed"))?.delivered == false)
    }

    @Test func ignoresTheMacsOwnPendingWrite() {
        #expect(ReplyResult.from(row(status: "pending")) == nil)
    }

    @Test func ignoresARowWithNoReplyStatus() {
        #expect(ReplyResult.from(row(status: nil)) == nil)
    }

    @Test func ignoresAnOutcomeWithNoText() {
        #expect(ReplyResult.from(row(status: "failed", text: nil)) == nil)
    }

    @Test func decodesARowFromTheWire() throws {
        let decoded = try decode("""
        {"notification_key": "key", "app_name": "Signal",
         "reply_text": "on my way", "reply_status": "failed"}
        """)

        #expect(ReplyResult.from(decoded)?.delivered == false)
    }

    @Test func decodesARowWhoseReplyColumnsAreStillNull() throws {
        let decoded = try decode("""
        {"id": 42, "notification_key": "key", "app_name": "Signal",
         "reply_text": null, "reply_status": null, "is_dismissed": false}
        """)

        #expect(ReplyResult.from(decoded) == nil)
    }
}
