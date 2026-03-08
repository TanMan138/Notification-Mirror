import AppKit

final class AppDelegate: NSObject, NSApplicationDelegate {
    func applicationDidFinishLaunching(_ notification: Notification) {
        Task {
            try? await NotificationManager.shared.requestAuthorization()
        }
    }
}
