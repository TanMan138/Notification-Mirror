import Foundation
import ServiceManagement

private let appFilterKey = "com.androidnotificationmirror.appFilter"
private let appIconsKey = "com.androidnotificationmirror.appIcons"

@Observable
final class MenuBarViewModel {
    static let shared = MenuBarViewModel()

    var realtimeConnectionStatus: RealtimeConnectionStatus = .disconnected
    var appFilter: [String: Bool] = [:]
    var appIcons: [String: String] = [:]
    var launchAtLogin: Bool = false

    enum RealtimeConnectionStatus: String {
        case connected = "Connected"
        case disconnected = "Disconnected"
    }

    init() {
        loadAppFilter()
        launchAtLogin = SMAppService.mainApp.status == .enabled
    }

    func toggleLaunchAtLogin(_ enabled: Bool) {
        do {
            if enabled {
                try SMAppService.mainApp.register()
            } else {
                try SMAppService.mainApp.unregister()
            }
            launchAtLogin = SMAppService.mainApp.status == .enabled
        } catch {
            launchAtLogin = SMAppService.mainApp.status == .enabled
        }
    }

    private func loadAppFilter() {
        if let data = UserDefaults.standard.data(forKey: appFilterKey),
           let decoded = try? JSONDecoder().decode([String: Bool].self, from: data) {
            appFilter = decoded
        }
        if let data = UserDefaults.standard.data(forKey: appIconsKey),
           let decoded = try? JSONDecoder().decode([String: String].self, from: data) {
            appIcons = decoded
        }
    }

    private func saveAppFilter() {
        if let data = try? JSONEncoder().encode(appFilter) {
            UserDefaults.standard.set(data, forKey: appFilterKey)
        }
        if let data = try? JSONEncoder().encode(appIcons) {
            UserDefaults.standard.set(data, forKey: appIconsKey)
        }
    }

    /// Registers an app if new (default enabled), returns whether to show the notification.
    @MainActor
    func shouldShowNotification(forApp appName: String?, appIconBase64: String? = nil) -> Bool {
        let name = appName?.trimmingCharacters(in: .whitespaces).isEmpty == false ? appName! : "Unknown"
        if appFilter[name] == nil {
            appFilter[name] = true
            if let icon = appIconBase64 {
                appIcons[name] = icon
            }
            saveAppFilter()
        } else if let icon = appIconBase64, appIcons[name] == nil {
            appIcons[name] = icon
            saveAppFilter()
        }
        return appFilter[name] ?? true
    }

    func setAppEnabled(_ name: String, _ enabled: Bool) {
        appFilter[name] = enabled
        saveAppFilter()
    }

}
