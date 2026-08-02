import Foundation

/// Outcome of probing a user's project during setup.
enum ConnectionCheck: Equatable {
    case ok
    case invalidCredentials
    case tableMissing

    /// The table exists but is not streamed, so nothing would ever be delivered.
    /// Also covers a setup that predates the status function.
    case realtimeNotReady
    case unreachable
    case unknown(String)

    var userMessage: String {
        switch self {
        case .ok:
            return "Connected."
        case .invalidCredentials:
            return "That key was rejected. Copy the publishable key from Project Settings, API."
        case .tableMissing:
            return "Connected, but the notifications table is missing. Run the SQL from the phone first."
        case .realtimeNotReady:
            return "Connected, but the table is not set up for realtime, so nothing would be "
                + "mirrored. Re-run the setup SQL from the phone — it is safe to re-run."
        case .unreachable:
            return "Could not reach the project. Check the URL and your connection."
        case .unknown(let detail):
            return "Something went wrong: \(detail)"
        }
    }
}
