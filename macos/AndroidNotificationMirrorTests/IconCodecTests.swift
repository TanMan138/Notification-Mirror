import AppKit
import Foundation
import Testing
@testable import AndroidNotificationMirror

@Suite struct IconCodecTests {

    /// A 1x1 red PNG, Base64 encoded.
    private static func onePixelPNGBase64() throws -> String {
        let image = NSImage(size: NSSize(width: 1, height: 1))
        image.lockFocus()
        NSColor.red.setFill()
        NSRect(x: 0, y: 0, width: 1, height: 1).fill()
        image.unlockFocus()

        let tiff = try #require(image.tiffRepresentation)
        let rep = try #require(NSBitmapImageRep(data: tiff))
        let png = try #require(rep.representation(using: .png, properties: [:]))
        return png.base64EncodedString()
    }

    @Test func writesADecodableImageToATemporaryFile() throws {
        let url = try IconCodec.pngFileURL(fromBase64: Self.onePixelPNGBase64())
        defer { try? FileManager.default.removeItem(at: url) }

        #expect(url.pathExtension == "png")
        #expect(FileManager.default.fileExists(atPath: url.path))
        #expect(NSImage(contentsOf: url) != nil)
    }

    @Test func rejectsAStringThatIsNotBase64() {
        #expect(throws: IconCodec.CodecError.self) {
            try IconCodec.pngFileURL(fromBase64: "not base64 at all!!")
        }
    }

    @Test func rejectsBase64ThatIsNotAnImage() {
        let notAnImage = Data("hello".utf8).base64EncodedString()
        #expect(throws: IconCodec.CodecError.self) {
            try IconCodec.pngFileURL(fromBase64: notAnImage)
        }
    }

    @Test func producesADistinctFilePerCall() throws {
        let base64 = try Self.onePixelPNGBase64()
        let first = try IconCodec.pngFileURL(fromBase64: base64)
        let second = try IconCodec.pngFileURL(fromBase64: base64)
        defer {
            try? FileManager.default.removeItem(at: first)
            try? FileManager.default.removeItem(at: second)
        }

        #expect(first != second)
    }
}
