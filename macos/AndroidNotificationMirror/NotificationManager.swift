import UserNotifications
import Supabase

private let DISMISS_ACTION = "DISMISS_ACTION"
private let MIRROR_CATEGORY = "MIRROR_NOTIFICATION"

struct SupabaseNotification: Decodable {
    let notification_key: String
    let app_name: String
    let title: String
    let body_text: String
    let base64_icon: String?
}

final class NotificationManager: NSObject, UNUserNotificationCenterDelegate {
    static let shared = NotificationManager()

    private let center = UNUserNotificationCenter.current()
    private let supabase = SupabaseClient(
        supabaseURL: URL(string: Config.supabaseURL)!,
        supabaseKey: Config.supabasePublishableKey,
        options: SupabaseClientOptions(
            auth: SupabaseClientOptions.AuthOptions(
                emitLocalSessionAsInitialSession: true
            )
        )
    )

    override init() {
        super.init()
        center.delegate = self
        registerCategory()
    }

    private func registerCategory() {
        let dismissAction = UNNotificationAction(
            identifier: DISMISS_ACTION,
            title: "Clear on Phone",
            options: []
        )
        let category = UNNotificationCategory(
            identifier: MIRROR_CATEGORY,
            actions: [dismissAction],
            intentIdentifiers: [],
            options: []
        )
        center.setNotificationCategories([category])
    }

    /// Requests notification authorization from the user. Call when the app starts.
    func requestAuthorization() async throws {
        let options: UNAuthorizationOptions = [.alert, .sound, .badge]
        try await center.requestAuthorization(options: options)
    }

    func startListeningForCloudNotifications() async {
        let channel = supabase.channel("public:notifications")
        let changeStream = channel.postgresChange(
            InsertAction.self,
            schema: "public",
            table: "notifications"
        )

        await channel.subscribe()

        for await change in changeStream {
            do {
                let notification = try change.record.decode(as: SupabaseNotification.self)

                // Check user preferences synchronously on the Main thread before showing
                let shouldShow = await MainActor.run {
                    MenuBarViewModel.shared.shouldShowNotification(
                        forApp: notification.app_name,
                        appIconBase64: notification.base64_icon
                    )
                }

                if shouldShow {
                    // We map this remote event directly to our local showNotification call
                    try await self.showNotification(
                        title: notification.title,
                        body: notification.body_text,
                        notificationKey: notification.notification_key,
                        phoneIp: "supabase", // Placeholder to keep signature backward-compatible
                        appName: notification.app_name,
                        appIconBase64: notification.base64_icon
                    )
                }
            } catch {
                #if DEBUG
                print("[NotificationMirror] Failed to decode SupabaseNotification: \(error)")
                #endif
            }
        }
    }

    /// Creates a local notification with the given title and body and triggers it immediately.
    func showNotification(
        title: String,
        body: String,
        notificationKey: String,
        phoneIp: String,
        appName: String? = nil,
        appIconBase64: String? = nil
    ) async throws {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default
        content.categoryIdentifier = MIRROR_CATEGORY
        content.userInfo = [
            "notificationKey": notificationKey,
            "phoneIp": phoneIp
        ]

        if let appName {
            content.subtitle = appName
        }

        if let appIconBase64, let imageData = Data(base64Encoded: appIconBase64) {
            let tempURL = FileManager.default.temporaryDirectory
                .appendingPathComponent(UUID().uuidString, isDirectory: false)
                .appendingPathExtension("webp")
            try imageData.write(to: tempURL)
            let options: [AnyHashable: Any] = [
                UNNotificationAttachmentOptionsTypeHintKey: "public.webp"
            ]
            let attachment = try UNNotificationAttachment(
                identifier: "appIcon",
                url: tempURL,
                options: options
            )
            content.attachments = [attachment]
        }

        let request = UNNotificationRequest(
            identifier: UUID().uuidString,
            content: content,
            trigger: UNTimeIntervalNotificationTrigger(timeInterval: 1, repeats: false)
        )
        try await center.add(request)
    }

    // MARK: - UNUserNotificationCenterDelegate

    /// Show notifications even when the app is in the foreground (active).
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound, .badge])
    }

    /// Handle notification action responses (e.g. "Clear on Phone").
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        defer { completionHandler() }

        guard response.actionIdentifier == DISMISS_ACTION else { return }

        let userInfo = response.notification.request.content.userInfo
        guard let notificationKey = userInfo["notificationKey"] as? String else {
            #if DEBUG
            print("[NotificationMirror] Dismiss failed: missing notificationKey in userInfo")
            #endif
            return
        }

        Task {
            await dismissOnPhone(notificationKey: notificationKey)
        }
    }

    func dismissOnPhone(notificationKey: String) async {
        do {
            try await supabase
                .from("notifications")
                .update(["is_dismissed": true])
                .eq("notification_key", value: notificationKey)
                .execute()
            #if DEBUG
            print("[NotificationMirror] Notification \(notificationKey) marked as dismissed on phone.")
            #endif
        } catch {
            #if DEBUG
            print("[NotificationMirror] Failed to mark notification as dismissed: \(error.localizedDescription)")
            #endif
        }
    }
}
