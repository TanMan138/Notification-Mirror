import Foundation
import Testing
@testable import AndroidNotificationMirror

@Suite struct SetupStatusCheckTests {

    @Test func acceptsAFullyConfiguredProject() {
        let status = SetupStatus(inPublication: true, replicaIdentityFull: true, replyColumns: true)

        #expect(NotificationRepository.setupCheck(status) == .ok)
    }

    @Test func reportsAnOutdatedSchemaWhenOnlyReplyColumnsAreMissing() {
        let status = SetupStatus(inPublication: true, replicaIdentityFull: true, replyColumns: false)

        #expect(NotificationRepository.setupCheck(status) == .setupOutdated)
    }

    @Test func prefersTheRealtimeFailureWhenBothAreWrong() {
        let status = SetupStatus(inPublication: false, replicaIdentityFull: true, replyColumns: false)

        #expect(NotificationRepository.setupCheck(status) == .realtimeNotReady)
    }

    @Test func treatsAMissingStatusRowAsAnOlderSetup() {
        #expect(NotificationRepository.setupCheck(nil) == .realtimeNotReady)
    }

    @Test func decodesAStatusRowMissingTheReplyColumnsField() throws {
        let json = Data(#"{"in_publication": true, "replica_identity_full": true}"#.utf8)

        let status = try JSONDecoder().decode(SetupStatus.self, from: json)

        #expect(status.replyColumns == false)
    }
}
