import AppKit
import Foundation

/// Converts the phone's Base64 app icon into a file a notification can attach.
///
/// The phone sends lossy WebP to keep payloads small, but UNNotificationAttachment
/// rejects WebP on macOS with UNErrorDomain 101, so the icon is re-encoded as PNG.
enum IconCodec {

    enum CodecError: Error {
        case notBase64
        case notAnImage
        case pngEncodingFailed
    }

    static func pngFileURL(fromBase64 base64: String) throws -> URL {
        guard let data = Data(base64Encoded: base64) else {
            throw CodecError.notBase64
        }
        guard let image = NSImage(data: data) else {
            throw CodecError.notAnImage
        }
        guard let tiff = image.tiffRepresentation,
              let rep = NSBitmapImageRep(data: tiff),
              let png = rep.representation(using: .png, properties: [:]) else {
            throw CodecError.pngEncodingFailed
        }

        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: false)
            .appendingPathExtension("png")
        try png.write(to: url)
        return url
    }
}
