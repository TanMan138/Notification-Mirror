import Foundation

enum PairingError: Error, Equatable {
    case malformed
    case unsupportedVersion
    case expired
}

private let pairingVersion = 1
private let maxAgeSeconds: Int64 = 300

/// Tolerates a little clock skew between the two devices.
private let maxSkewSeconds: Int64 = 60

enum PairingPayload {

    private struct Wire: Codable {
        let v: Int
        let url: String
        let key: String
        let iat: Int64
    }

    static func decode(_ encoded: String, now: Int64) -> Result<SupabaseConfig, Error> {
        let trimmed = encoded.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let data = base64URLDecode(trimmed),
              let wire = try? JSONDecoder().decode(Wire.self, from: data) else {
            return .failure(PairingError.malformed)
        }

        guard wire.v == pairingVersion else { return .failure(PairingError.unsupportedVersion) }

        let age = now - wire.iat
        guard age <= maxAgeSeconds, age >= -maxSkewSeconds else {
            return .failure(PairingError.expired)
        }

        return SupabaseConfig.parse(url: wire.url, key: wire.key).mapError { $0 as Error }
    }

    private static func base64URLDecode(_ string: String) -> Data? {
        var base64 = string
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        while base64.count % 4 != 0 { base64 += "=" }
        return Data(base64Encoded: base64)
    }
}
