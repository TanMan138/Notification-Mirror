import Foundation
import Testing
@testable import AndroidNotificationMirror

@MainActor
@Suite struct MenuBarViewModelTests {

    private func isolatedDefaults() -> UserDefaults {
        let suiteName = "test.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defaults.removePersistentDomain(forName: suiteName)
        return defaults
    }

    @Test func registersAnUnseenAppAsEnabled() {
        let viewModel = MenuBarViewModel(defaults: isolatedDefaults())

        #expect(viewModel.shouldShowNotification(forApp: "Signal", appIconBase64: nil))
        #expect(viewModel.appFilter["Signal"] == true)
    }

    @Test func honoursADisabledApp() {
        let viewModel = MenuBarViewModel(defaults: isolatedDefaults())
        _ = viewModel.shouldShowNotification(forApp: "Slack", appIconBase64: nil)
        viewModel.setAppEnabled("Slack", false)

        #expect(viewModel.shouldShowNotification(forApp: "Slack", appIconBase64: nil) == false)
    }

    @Test func fallsBackToUnknownForANilAppName() {
        let viewModel = MenuBarViewModel(defaults: isolatedDefaults())

        _ = viewModel.shouldShowNotification(forApp: nil, appIconBase64: nil)
        #expect(viewModel.appFilter["Unknown"] == true)
    }

    @Test func fallsBackToUnknownForAWhitespaceAppName() {
        let viewModel = MenuBarViewModel(defaults: isolatedDefaults())

        _ = viewModel.shouldShowNotification(forApp: "   ", appIconBase64: nil)
        #expect(viewModel.appFilter["Unknown"] == true)
    }

    @Test func storesTheFirstIconItSeesForAnApp() {
        let viewModel = MenuBarViewModel(defaults: isolatedDefaults())

        _ = viewModel.shouldShowNotification(forApp: "Signal", appIconBase64: "FIRST")
        _ = viewModel.shouldShowNotification(forApp: "Signal", appIconBase64: "SECOND")

        #expect(viewModel.appIcons["Signal"] == "FIRST")
    }

    @Test func backfillsAnIconForAnAppFirstSeenWithout() {
        let viewModel = MenuBarViewModel(defaults: isolatedDefaults())

        _ = viewModel.shouldShowNotification(forApp: "Signal", appIconBase64: nil)
        _ = viewModel.shouldShowNotification(forApp: "Signal", appIconBase64: "LATER")

        #expect(viewModel.appIcons["Signal"] == "LATER")
    }

    @Test func persistsAcrossInstances() {
        let defaults = isolatedDefaults()
        let first = MenuBarViewModel(defaults: defaults)
        _ = first.shouldShowNotification(forApp: "Slack", appIconBase64: "ICON")
        first.setAppEnabled("Slack", false)

        let second = MenuBarViewModel(defaults: defaults)
        #expect(second.appFilter["Slack"] == false)
        #expect(second.appIcons["Slack"] == "ICON")
    }
}
