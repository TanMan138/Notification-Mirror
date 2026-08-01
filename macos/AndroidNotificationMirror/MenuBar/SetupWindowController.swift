import AppKit
import SwiftUI

/// The app is LSUIElement, so the setup window has to be created and focused by hand.
@MainActor
final class SetupWindowController {

    static let shared = SetupWindowController()

    var configStore: ConfigStore?
    private var window: NSWindow?

    func show() {
        if let window {
            window.makeKeyAndOrderFront(nil)
            NSApp.activate(ignoringOtherApps: true)
            return
        }

        let view = SetupView { [weak self] config in
            self?.configStore?.save(config)
            self?.close()
        }

        let window = NSWindow(
            contentRect: NSRect(x: 0, y: 0, width: 460, height: 420),
            styleMask: [.titled, .closable],
            backing: .buffered,
            defer: false
        )
        window.title = "Notification Mirror Setup"
        window.contentView = NSHostingView(rootView: view)
        window.center()
        window.isReleasedWhenClosed = false
        self.window = window

        window.makeKeyAndOrderFront(nil)
        NSApp.activate(ignoringOtherApps: true)
    }

    private func close() {
        window?.close()
    }
}
