import Foundation
import Testing
@testable import AndroidNotificationMirror

@Suite struct PairingPayloadTests {

    private let config = SupabaseConfig(
        url: "https://abcdefgh.supabase.co",
        publishableKey: "sb_publishable_abcdefghijklmnop"
    )
    private let issuedAt: Int64 = 1_753_939_200

    /// Shared with the Android suite. Changing this is a breaking format change.
    private let fixedVector =
        "eyJ2IjoxLCJ1cmwiOiJodHRwczovL2FiY2RlZmdoLnN1cGFiYXNlLmNvIiwia2V5Ijoic2JfcHVibGlzaGFibGVfYWJjZGVmZ2hpamtsbW5vcCIsImlhdCI6MTc1MzkzOTIwMH0"

    private func error(_ encoded: String, now: Int64) -> Error? {
        if case .failure(let e) = PairingPayload.decode(encoded, now: now) { return e }
        return nil
    }

    @Test func decodesTheVectorProducedByAndroid() throws {
        let decoded = try PairingPayload.decode(fixedVector, now: issuedAt + 10).get()

        #expect(decoded == config)
    }

    @Test func acceptsAPayloadFourMinutesOld() throws {
        let decoded = try PairingPayload.decode(fixedVector, now: issuedAt + 240).get()

        #expect(decoded == config)
    }

    @Test func rejectsAPayloadSixMinutesOld() {
        #expect(error(fixedVector, now: issuedAt + 360) as? PairingError == .expired)
    }

    @Test func rejectsAStringThatIsNotBase64() {
        #expect(error("not a pairing string!!", now: issuedAt) as? PairingError == .malformed)
    }

    @Test func rejectsATruncatedPayload() {
        #expect(error(String(fixedVector.dropLast(20)), now: issuedAt) as? PairingError == .malformed)
    }
}
