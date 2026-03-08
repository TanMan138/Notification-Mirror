import Foundation
import Swifter

final class ServerManager {
    static let shared = ServerManager()

    private let server = HttpServer()
    private let port: UInt16 = 8080
    private let queue = DispatchQueue(label: "com.notificationmirror.server", qos: .userInitiated)

    private init() {}

    /// Starts the HTTP server on port 8080 in the background.
    func start() {
        server["/ping"] = { _ in .ok(.html("pong")) }

        server.POST["/notify"] = { [weak self] request in
            self?.handleNotify(request: request) ?? .internalServerError
        }

        queue.async { [weak self] in
            guard let self else { return }
            do {
                try self.server.start(self.port)
            } catch {
                print("Server failed to start: \(error)")
            }
        }
    }

    /// Stops the HTTP server.
    func stop() {
        server.stop()
    }

    private func handleNotify(request: HttpRequest) -> HttpResponse {
        let data = Data(request.body)

        struct NotifyPayload: Codable {
            let title: String
            let text: String
            let secret: String
            let notificationKey: String
            let phoneIp: String
            let appName: String?
            let appIconBase64: String?
        }

        guard !data.isEmpty else {
            return .badRequest(.json(["error": "Missing request body"] as [String: String]))
        }

        do {
            let payload = try JSONDecoder().decode(NotifyPayload.self, from: data)
            guard let expectedSecret = ProcessInfo.processInfo.environment["NOTIFICATION_MIRROR_SECRET"],
                  !expectedSecret.isEmpty,
                  payload.secret == expectedSecret else {
                return .unauthorized
            }
            let shouldShow: Bool = DispatchQueue.main.sync {
                MenuBarViewModel.shared.shouldShowNotification(forApp: payload.appName, appIconBase64: payload.appIconBase64)
            }
            guard shouldShow else {
                return .ok(.json(["status": "ok"] as [String: String]))
            }
            Task {
                try? await NotificationManager.shared.showNotification(
                    title: payload.title,
                    body: payload.text,
                    notificationKey: payload.notificationKey,
                    phoneIp: payload.phoneIp,
                    appName: payload.appName,
                    appIconBase64: payload.appIconBase64
                )
            }
            return .ok(.json(["status": "ok"] as [String: String]))
        } catch {
            return .badRequest(.json(["error": "Invalid JSON: expected { \"title\": \"...\", \"text\": \"...\", \"secret\": \"...\", \"notificationKey\": \"...\", \"phoneIp\": \"...\" }"] as [String: String]))
        }
    }
}
