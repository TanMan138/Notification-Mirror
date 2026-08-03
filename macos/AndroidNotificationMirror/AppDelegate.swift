import AppKit
import OSLog

private let log = Logger(subsystem: "com.tanman138.AndroidNotificationMirror", category: "AppDelegate")

// AppKit delivers these callbacks on the main thread, and the stores it owns are
// main-actor isolated. Stating it keeps older toolchains from inferring otherwise.
@MainActor
final class AppDelegate: NSObject, NSApplicationDelegate {

    private let configStore = ConfigStore()
    private var repository: NotificationRepository?
    private var presenter: NotificationPresenter?
    private var pipeline: Task<Void, Never>?

    /// Lets the setup window read and write the same store.
    var sharedConfigStore: ConfigStore { configStore }

    func applicationDidFinishLaunching(_ notification: Notification) {
        let presenter = NotificationPresenter { [weak self] key in
            Task { await self?.repository?.markDismissed(notificationKey: key) }
        } onReplyRequested: { [weak self] key, text in
            Task { await self?.repository?.sendReply(notificationKey: key, text: text) }
        }
        self.presenter = presenter
        SetupWindowController.shared.configStore = configStore

        Task {
            await presenter.requestAuthorization()
            for await config in configStore.configChanges() {
                await restart(with: config, presenter: presenter)
            }
        }
    }

    /// Cancelling the previous pipeline terminates its AsyncStreams, which
    /// releases the old realtime channel before the new client subscribes.
    private func restart(with config: SupabaseConfig?, presenter: NotificationPresenter) async {
        pipeline?.cancel()
        pipeline = nil
        repository = nil

        await MainActor.run { MenuBarViewModel.shared.isConfigured = config != nil }

        guard let config, let client = SupabaseProvider.makeClient(config) else {
            log.error("Supabase is not configured. Open Set Up… from the menu bar.")
            await MainActor.run { MenuBarViewModel.shared.realtimeConnectionStatus = .disconnected }
            return
        }

        let repository = NotificationRepository(client: client)
        self.repository = repository

        pipeline = Task {
            await withTaskGroup(of: Void.self) { group in
                group.addTask { await self.consumeEvents(from: repository, into: presenter) }
                group.addTask {
                    for await status in repository.socketStatus() {
                        await MainActor.run {
                            MenuBarViewModel.shared.realtimeConnectionStatus =
                                status == .connected ? .connected : .disconnected
                        }
                    }
                }
            }
        }
    }

    private func consumeEvents(
        from repository: NotificationRepository,
        into presenter: NotificationPresenter
    ) async {
        for await event in repository.mirrorEvents() {
            switch event {
            case .incoming(let notification):
                let shouldShow = await MainActor.run {
                    MenuBarViewModel.shared.shouldShowNotification(
                        forApp: notification.appName,
                        appIconBase64: notification.base64Icon
                    )
                }
                if shouldShow {
                    await presenter.present(notification)
                }
            case .replyResult(let result):
                // A delivered reply needs no announcement; a lost one does.
                if !result.delivered {
                    await presenter.presentReplyFailure(result)
                }
            }
        }
    }
}
