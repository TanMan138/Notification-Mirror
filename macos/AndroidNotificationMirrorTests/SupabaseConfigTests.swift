import Foundation
import Testing
@testable import AndroidNotificationMirror

@Suite struct SupabaseConfigTests {

    private let validKey = "sb_publishable_abcdefghijklmnop"

    private func error(_ url: String, _ key: String) -> ConfigError? {
        if case .failure(let e) = SupabaseConfig.parse(url: url, key: key) { return e }
        return nil
    }

    @Test func acceptsAWellFormedProjectURLAndKey() throws {
        let config = try SupabaseConfig.parse(url: "https://abcdefgh.supabase.co", key: validKey).get()

        #expect(config.url == "https://abcdefgh.supabase.co")
        #expect(config.publishableKey == validKey)
    }

    @Test func trimsWhitespaceAndTrailingSlash() throws {
        let config = try SupabaseConfig.parse(url: "  https://abcdefgh.supabase.co/  ", key: "  \(validKey)  ").get()

        #expect(config.url == "https://abcdefgh.supabase.co")
        #expect(config.publishableKey == validKey)
    }

    @Test func rejectsABlankURL() {
        #expect(error("   ", validKey) == .urlBlank)
    }

    @Test func rejectsPlainHTTP() {
        #expect(error("http://abcdefgh.supabase.co", validKey) == .urlNotHTTPS)
    }

    @Test func rejectsANonSupabaseHost() {
        #expect(error("https://example.com", validKey) == .urlNotSupabase)
    }

    @Test func rejectsABlankKey() {
        #expect(error("https://abcdefgh.supabase.co", "  ") == .keyBlank)
    }

    @Test func rejectsANewFormatSecretKey() {
        #expect(error("https://abcdefgh.supabase.co", "sb_secret_abcdefghijklmnop") == .keyIsSecret)
    }

    @Test func rejectsAServiceRoleJWT() {
        let jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9."
            + "eyJyb2xlIjoic2VydmljZV9yb2xlIiwicmVmIjoiYWJjIn0."
            + "c2lnbmF0dXJl"

        #expect(error("https://abcdefgh.supabase.co", jwt) == .keyIsSecret)
    }

    @Test func acceptsALegacyAnonJWT() {
        let jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9."
            + "eyJyb2xlIjoiYW5vbiIsInJlZiI6ImFiYyJ9."
            + "c2lnbmF0dXJl"

        #expect(error("https://abcdefgh.supabase.co", jwt) == nil)
    }
}
