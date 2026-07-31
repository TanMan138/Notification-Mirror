import Testing
@testable import AndroidNotificationMirror

@Test func testTargetIsWiredUp() {
    #expect(Config.supabaseURL.hasPrefix("https://") || Config.supabaseURL.isEmpty)
}
