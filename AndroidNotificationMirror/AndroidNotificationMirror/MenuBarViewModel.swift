import Foundation
import ServiceManagement

private let appFilterKey = "com.androidnotificationmirror.appFilter"
private let appIconsKey = "com.androidnotificationmirror.appIcons"

@Observable
final class MenuBarViewModel {
    static let shared = MenuBarViewModel()

    var localIPAddress: String = "—"
    var serverStatus: ServerStatus = .running
    var appFilter: [String: Bool] = [:]
    var appIcons: [String: String] = [:]
    var launchAtLogin: Bool = false

    enum ServerStatus: String {
        case running = "Running"
        case stopped = "Stopped"
    }

    init() {
        loadAppFilter()
        fetchLocalIP()
        ServerManager.shared.start()
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

    func fetchLocalIP() {
        // Prefer WiFi (en0) IPv4, fallback to first non-loopback IPv4
        localIPAddress = Self.getLocalIPAddress() ?? "—"
    }

    func toggleServer() {
        switch serverStatus {
        case .running:
            ServerManager.shared.stop()
            serverStatus = .stopped
        case .stopped:
            ServerManager.shared.start()
            serverStatus = .running
        }
    }

    /// Returns the primary local IPv4 address (en0 preferred), or nil if none found.
    private static func getLocalIPAddress() -> String? {
        var _: String?
        var ifaddr: UnsafeMutablePointer<ifaddrs>?

        guard getifaddrs(&ifaddr) == 0, let first = ifaddr else { return nil }
        defer { freeifaddrs(ifaddr) }

        var en0Address: String?
        var fallbackAddress: String?

        var ptr = first
        while true {
            let interface = ptr.pointee
            let addrFamily = interface.ifa_addr.pointee.sa_family

            if addrFamily == UInt8(AF_INET) {
                let name = String(cString: interface.ifa_name)
                if name == "en0" {
                    en0Address = Self.addressString(from: interface.ifa_addr)
                } else if name != "lo0" && fallbackAddress == nil {
                    fallbackAddress = Self.addressString(from: interface.ifa_addr)
                }
            }
            guard let next = interface.ifa_next else { break }
            ptr = next
        }

        return en0Address ?? fallbackAddress
    }

    private static func addressString(from addr: UnsafePointer<sockaddr>) -> String? {
        var hostname = [CChar](repeating: 0, count: Int(NI_MAXHOST))
        let result = getnameinfo(
            addr,
            socklen_t(addr.pointee.sa_len),
            &hostname,
            socklen_t(hostname.count),
            nil,
            0,
            NI_NUMERICHOST
        )
        guard result == 0 else { return nil }
        return String(cString: hostname)
    }
}
