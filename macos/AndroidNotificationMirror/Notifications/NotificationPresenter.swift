import Foundation
import OSLog
import UserNotifications

private let log = Logger(subsystem: "com.tanman138.AndroidNotificationMirror", category: "Presenter")

private let dismissActionIdentifier = "DISMISS_ACTION"
private let mirrorCategoryIdentifier = "MIRROR_NOTIFICATION"

/// Owns everything UNUserNotificationCenter. Reports dismissals through a closure
/// rather than writing to Supabase itself.
final class NotificationPresenter: NSObject, UNUserNotificationCenterDelegate {

    private let center = UNUserNotificationCenter.current()
    private let onDismissRequested: (String) -> Void

    init(onDismissRequested: @escaping (String) -> Void) {
        self.onDismissRequested = onDismissRequested
        super.init()
        center.delegate = self
        registerCategory()
    }

    private func registerCategory() {
        let dismiss = UNNotificationAction(
            identifier: dismissActionIdentifier,
            title: "Clear on Phone",
            options: []
        )
        center.setNotificationCategories([
            UNNotificationCategory(
                identifier: mirrorCategoryIdentifier,
                actions: [dismiss],
                intentIdentifiers: [],
                options: []
            )
        ])
    }

    func requestAuthorization() async {
        do {
            _ = try await center.requestAuthorization(options: [.alert, .sound, .badge])
        } catch {
            log.error("Notification authorization failed: \(error.localizedDescription)")
        }
    }

    func present(_ notification: MirroredNotification) async {
        let content = UNMutableNotificationContent()
        content.title = notification.title
        content.body = notification.bodyText ?? ""
        content.sound = .default
        content.categoryIdentifier = mirrorCategoryIdentifier
        content.userInfo = ["notificationKey": notification.notificationKey]

        if let appName = notification.appName {
            content.subtitle = appName
        }

        if let base64 = notification.base64Icon {
            do {
                let url = try IconCodec.pngFileURL(fromBase64: base64)
                content.attachments = [
                    try UNNotificationAttachment(
                        identifier: "appIcon",
                        url: url,
                        options: [UNNotificationAttachmentOptionsTypeHintKey: "public.png"]
                    )
                ]
            } catch {
                // Deliver without the icon rather than dropping the notification.
                log.error("Icon attachment failed: \(error.localizedDescription)")
            }
        }

        let request = UNNotificationRequest(
            identifier: UUID().uuidString,
            content: content,
            trigger: nil
        )

        do {
            try await center.add(request)
        } catch {
            log.error("Could not post notification: \(error.localizedDescription)")
        }
    }

    // MARK: - UNUserNotificationCenterDelegate

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound, .badge])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        defer { completionHandler() }

        guard response.actionIdentifier == dismissActionIdentifier else { return }
        guard let key = response.notification.request.content.userInfo["notificationKey"] as? String else {
            log.error("Dismiss action carried no notificationKey.")
            return
        }
        onDismissRequested(key)
    }
}
