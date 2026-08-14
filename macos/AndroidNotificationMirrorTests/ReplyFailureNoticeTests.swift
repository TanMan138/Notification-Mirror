import Foundation
import Testing
@testable import AndroidNotificationMirror

@Suite struct ReplyFailureNoticeTests {

    @Test func namesTheAppWhenItIsKnown() {
        #expect(ReplyFailureNotice.title(appName: "Signal") == "Reply to Signal not delivered")
    }

    @Test func fallsBackWhenTheAppIsUnknown() {
        #expect(ReplyFailureNotice.title(appName: nil) == "Reply not delivered")
    }

    @Test func fallsBackWhenTheAppNameIsEmpty() {
        #expect(ReplyFailureNotice.title(appName: "") == "Reply not delivered")
    }

    @Test func keepsTheUndeliveredTextSoItCanBeCopied() {
        #expect(
            ReplyFailureNotice.body(text: "on my way")
                == "The notification is no longer on your phone. Your message: on my way"
        )
    }
}
