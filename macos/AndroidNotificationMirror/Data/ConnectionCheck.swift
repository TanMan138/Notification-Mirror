import Foundation

/// Outcome of probing a user's project during setup.
enum ConnectionCheck: Equatable {
    case ok
    case invalidCredentials
    case tableMissing
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
        case .unreachable:
            return "Could not reach the project. Check the URL and your connection."
        case .unknown(let detail):
            return "Something went wrong: \(detail)"
        }
    }
}
