import XCTest
@testable import AndroidNotificationMirror

final class SetupStatusCheckTests: XCTestCase {

    func testAcceptsAFullyConfiguredProject() {
        let status = SetupStatus(inPublication: true, replicaIdentityFull: true, replyColumns: true)

        XCTAssertEqual(NotificationRepository.setupCheck(status), .ok)
    }

    func testReportsAnOutdatedSchemaWhenOnlyReplyColumnsAreMissing() {
        let status = SetupStatus(inPublication: true, replicaIdentityFull: true, replyColumns: false)

        XCTAssertEqual(NotificationRepository.setupCheck(status), .setupOutdated)
    }

    func testPrefersTheRealtimeFailureWhenBothAreWrong() {
        let status = SetupStatus(inPublication: false, replicaIdentityFull: true, replyColumns: false)

        XCTAssertEqual(NotificationRepository.setupCheck(status), .realtimeNotReady)
    }

    func testTreatsAMissingStatusRowAsAnOlderSetup() {
        XCTAssertEqual(NotificationRepository.setupCheck(nil), .realtimeNotReady)
    }

    func testDecodesAStatusRowMissingTheReplyColumnsField() throws {
        let json = Data(#"{"in_publication":true,"replica_identity_full":true}"#.utf8)

        let status = try JSONDecoder().decode(SetupStatus.self, from: json)

        XCTAssertFalse(status.replyColumns)
    }
}
