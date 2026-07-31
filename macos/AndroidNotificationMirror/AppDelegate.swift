import AppKit
import OSLog

private let log = Logger(subsystem: "com.tanman138.AndroidNotificationMirror", category: "AppDelegate")

final class AppDelegate: NSObject, NSApplicationDelegate {

    private var repository: NotificationRepository?
    private var presenter: NotificationPresenter?

    func applicationDidFinishLaunching(_ notification: Notification) {
        guard let client = SupabaseProvider.makeClient() else {
            log.error("Supabase is not configured. Fill in Secrets.xcconfig and rebuild.")
            return
        }

        let repository = NotificationRepository(client: client)
        let presenter = NotificationPresenter { key in
            Task { await repository.markDismissed(notificationKey: key) }
        }
        self.repository = repository
        self.presenter = presenter

        Task {
            await presenter.requestAuthorization()
            await consumeNotifications(from: repository, into: presenter)
        }

        Task {
            for await status in repository.socketStatus() {
                await MainActor.run {
                    MenuBarViewModel.shared.realtimeConnectionStatus =
                        status == .connected ? .connected : .disconnected
                }
            }
        }
    }

    private func consumeNotifications(
        from repository: NotificationRepository,
        into presenter: NotificationPresenter
    ) async {
        for await notification in repository.incomingNotifications() {
            let shouldShow = await MainActor.run {
                MenuBarViewModel.shared.shouldShowNotification(
                    forApp: notification.appName,
                    appIconBase64: notification.base64Icon
                )
            }
            if shouldShow {
                await presenter.present(notification)
            }
        }
    }
}
