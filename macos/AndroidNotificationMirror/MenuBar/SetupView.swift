import SwiftUI

struct SetupView: View {

    @State private var pairingString = ""
    @State private var url = ""
    @State private var key = ""
    @State private var status: String?
    @State private var isChecking = false

    let onSave: (SupabaseConfig) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Connect to your Supabase project")
                .font(.headline)

            GroupBox("Paste a pairing code from your phone") {
                VStack(alignment: .leading, spacing: 8) {
                    TextField("Pairing code", text: $pairingString)
                        .textFieldStyle(.roundedBorder)
                    Button("Pair") { pair() }
                        .disabled(isChecking || pairingString.isEmpty)
                }
                .padding(8)
            }

            GroupBox("Or enter details manually") {
                VStack(alignment: .leading, spacing: 8) {
                    TextField("Project URL", text: $url)
                        .textFieldStyle(.roundedBorder)
                    TextField("Publishable key", text: $key)
                        .textFieldStyle(.roundedBorder)
                    Button("Test connection") { checkManual() }
                        .disabled(isChecking || url.isEmpty || key.isEmpty)
                }
                .padding(8)
            }

            if let status {
                Text(status).font(.callout)
            }
        }
        .padding(20)
        .frame(width: 460)
    }

    private func pair() {
        let now = Int64(Date().timeIntervalSince1970)
        switch PairingPayload.decode(pairingString, now: now) {
        case .success(let config):
            verify(config)
        case .failure(let error):
            status = message(for: error)
        }
    }

    private func checkManual() {
        switch SupabaseConfig.parse(url: url, key: key) {
        case .success(let config):
            verify(config)
        case .failure(let error):
            status = message(for: error)
        }
    }

    private func verify(_ config: SupabaseConfig) {
        guard let client = SupabaseProvider.makeClient(config) else {
            status = "That URL could not be used."
            return
        }
        isChecking = true
        status = "Checking…"
        Task {
            let result = await NotificationRepository(client: client).checkConnection()
            await MainActor.run {
                isChecking = false
                status = result.userMessage
                if result == .ok { onSave(config) }
            }
        }
    }

    private func message(for error: Error) -> String {
        if let error = error as? PairingError {
            switch error {
            case .malformed: return "That does not look like a pairing code."
            case .unsupportedVersion: return "That code came from a different version of the app."
            case .expired: return "That code has expired. Generate a new one on your phone."
            }
        }
        if let error = error as? ConfigError {
            switch error {
            case .urlBlank: return "Enter your project URL."
            case .urlNotHTTPS: return "The URL must start with https://"
            case .urlNotSupabase: return "That does not look like a Supabase project URL."
            case .keyBlank: return "Enter your publishable key."
            case .keyIsSecret:
                return "That is a secret key. Use the publishable key instead — "
                    + "a secret key would give this app full access to your database."
            }
        }
        return "Something went wrong."
    }
}
