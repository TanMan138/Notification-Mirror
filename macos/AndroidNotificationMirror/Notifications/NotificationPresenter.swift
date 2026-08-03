import Foundation
import OSLog
import UserNotifications

private let log = Logger(subsystem: "com.tanman138.AndroidNotificationMirror", category: "Presenter")

private let dismissActionIdentifier = "DISMISS_ACTION"
private let replyActionIdentifier = "REPLY_ACTION"
private let mirrorCategoryIdentifier = "MIRROR_NOTIFICATION"
private let repliableCategoryIdentifier = "MIRROR_REPLIABLE"
private let failureCategoryIdentifier = "MIRROR_REPLY_FAILED"

/// Copy for a reply the phone could not deliver. Pure so it can be tested; the
/// delegate callback that triggers it cannot be.
enum ReplyFailureNotice {
    static func title(appName: String?) -> String {
        guard let appName, !appName.isEmpty else { return "Reply not delivered" }
        return "Reply to \(appName) not delivered"
    }

    static func body(text: String) -> String {
        "The notification is no longer on your phone. Your message: \(text)"
    }
}

/// Owns everything UNUserNotificationCenter. Reports dismissals through a closure
/// rather than writing to Supabase itself.
final class NotificationPresenter: NSObject, UNUserNotificationCenterDelegate {

    private let center = UNUserNotificationCenter.current()
    private let onDismissRequested: (String) -> Void
    private let onReplyRequested: (String, String) -> Void

    init(
        onDismissRequested: @escaping (String) -> Void,
        onReplyRequested: @escaping (String, String) -> Void
    ) {
        self.onDismissRequested = onDismissRequested
        self.onReplyRequested = onReplyRequested
        super.init()
        center.delegate = self
        registerCategories()
    }

    private func registerCategories() {
        let dismiss = UNNotificationAction(
            identifier: dismissActionIdentifier,
            title: "Clear on Phone",
            options: []
        )
        let reply = UNTextInputNotificationAction(
            identifier: replyActionIdentifier,
            title: "Reply",
            options: [],
            textInputButtonTitle: "Send",
            textInputPlaceholder: "Reply"
        )
        center.setNotificationCategories([
            UNNotificationCategory(
                identifier: mirrorCategoryIdentifier,
                actions: [dismiss],
                intentIdentifiers: [],
                options: []
            ),
            // Only notifications the phone flagged repliable get this one, so a
            // reply field never appears where the reply would have nowhere to go.
            UNNotificationCategory(
                identifier: repliableCategoryIdentifier,
                actions: [reply, dismiss],
                intentIdentifiers: [],
                options: []
            ),
            UNNotificationCategory(
                identifier: failureCategoryIdentifier,
                actions: [],
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
        content.categoryIdentifier = notification.isRepliable
            ? repliableCategoryIdentifier
            : mirrorCategoryIdentifier
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

    /// Announces a reply the phone could not deliver, carrying the text so it
    /// is not lost.
    func presentReplyFailure(_ result: ReplyResult) async {
        let content = UNMutableNotificationContent()
        content.title = ReplyFailureNotice.title(appName: result.appName)
        content.body = ReplyFailureNotice.body(text: result.text)
        content.sound = .default
        content.categoryIdentifier = failureCategoryIdentifier

        let request = UNNotificationRequest(
            identifier: UUID().uuidString,
            content: content,
            trigger: nil
        )

        do {
            try await center.add(request)
        } catch {
            log.error("Could not post the reply failure: \(error.localizedDescription)")
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

        guard let key = response.notification.request.content.userInfo["notificationKey"] as? String else {
            // The reply-failure notification carries no key and needs no action.
            return
        }

        if let textResponse = response as? UNTextInputNotificationResponse,
           textResponse.actionIdentifier == replyActionIdentifier {
            let text = textResponse.userText.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !text.isEmpty else { return }
            onReplyRequested(key, text)
            return
        }

        guard response.actionIdentifier == dismissActionIdentifier else { return }
        onDismissRequested(key)
    }
}
