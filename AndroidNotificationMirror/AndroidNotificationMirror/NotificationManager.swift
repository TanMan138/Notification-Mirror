import UserNotifications

private let DISMISS_ACTION = "DISMISS_ACTION"
private let MIRROR_CATEGORY = "MIRROR_NOTIFICATION"

final class NotificationManager: NSObject, UNUserNotificationCenterDelegate {
    static let shared = NotificationManager()

    private let center = UNUserNotificationCenter.current()

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
                .appendingPathExtension("png")
            try imageData.write(to: tempURL)
            let options: [AnyHashable: Any] = [
                UNNotificationAttachmentOptionsTypeHintKey: "public.png"
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
        guard let notificationKey = userInfo["notificationKey"] as? String,
              let phoneIp = userInfo["phoneIp"] as? String else {
            #if DEBUG
            print("[NotificationMirror] Dismiss failed: missing notificationKey or phoneIp in userInfo")
            #endif
            return
        }

        sendDismissRequest(phoneIp: phoneIp, notificationKey: notificationKey)
    }

    private func sendDismissRequest(phoneIp: String, notificationKey: String) {
        let url = URL(string: "http://\(phoneIp):8081/dismiss")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try? JSONSerialization.data(withJSONObject: ["key": notificationKey])
        request.timeoutInterval = 5

        URLSession.shared.dataTask(with: request) { _, response, error in
            #if DEBUG
            if let error {
                print("[NotificationMirror] Dismiss request failed: \(error.localizedDescription)")
            } else if let http = response as? HTTPURLResponse, http.statusCode != 200 {
                print("[NotificationMirror] Dismiss request returned status \(http.statusCode)")
            }
            #endif
        }.resume()
    }
}
