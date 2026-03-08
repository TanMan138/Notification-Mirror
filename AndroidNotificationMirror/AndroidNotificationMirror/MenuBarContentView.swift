import SwiftUI
import AppKit

struct MenuBarContentView: View {
    @Bindable var viewModel: MenuBarViewModel

    var body: some View {
        // App Notifications submenu — enable/disable per app (checkmark = enabled)
        Menu("App Notifications") {
            if viewModel.appFilter.isEmpty {
                Text("No apps discovered yet")
                    .disabled(true)
            } else {
                Button("Enable All") {
                    for appName in viewModel.appFilter.keys {
                        viewModel.setAppEnabled(appName, true)
                    }
                }
                Button("Disable All") {
                    for appName in viewModel.appFilter.keys {
                        viewModel.setAppEnabled(appName, false)
                    }
                }
                Divider()
                ForEach(viewModel.appFilter.keys.sorted(), id: \.self) { appName in
                    Toggle(isOn: Binding(
                        get: { viewModel.appFilter[appName] ?? true },
                        set: { viewModel.setAppEnabled(appName, $0) }
                    )) {
                        Label {
                            Text(appName)
                        } icon: {
                            AppIconView(base64: viewModel.appIcons[appName])
                        }
                    }
                }
            }
        }

        Divider()

        // Copy Server URL
        Button("Copy Server URL") {
            let url = viewModel.localIPAddress != "—" ? "http://\(viewModel.localIPAddress):8080" : ""
            NSPasteboard.general.clearContents()
            NSPasteboard.general.setString(url, forType: .string)
        }
        .disabled(viewModel.localIPAddress == "—")

        // Server toggle
        Toggle(viewModel.serverStatus == .running ? "Server: Running" : "Server: Stopped", isOn: Binding(
            get: { viewModel.serverStatus == .running },
            set: { _ in viewModel.toggleServer() }
        ))

        // Launch at Login
        Toggle("Launch at Login", isOn: Binding(
            get: { viewModel.launchAtLogin },
            set: { viewModel.toggleLaunchAtLogin($0) }
        ))

        Divider()

        Button("Quit Notification Mirror") {
            NSApplication.shared.terminate(nil)
        }
        .keyboardShortcut("q", modifiers: .command)
    }
}

private struct AppIconView: View {
    let base64: String?

    var body: some View {
        Group {
            if let base64, let data = Data(base64Encoded: base64), let nsImage = NSImage(data: data) {
                Image(nsImage: nsImage)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 16, height: 16)
                    .clipShape(RoundedRectangle(cornerRadius: 4))
            } else {
                Image(systemName: "app.fill")
                    .foregroundStyle(.secondary)
                    .frame(width: 16, height: 16)
            }
        }
    }
}

#Preview {
    VStack {
        Text("Menu preview — use menu bar icon")
        MenuBarContentView(viewModel: MenuBarViewModel.shared)
    }
    .padding()
}
