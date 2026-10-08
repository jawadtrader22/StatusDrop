package com.junkfood.seal

import com.junkfood.seal.util.FriendlyError
import org.junit.Assert.assertEquals
import org.junit.Test

class FriendlyErrorTest {
    @Test
    fun mapsYtDlpErrors() {
        mapOf(
                "ERROR: [Instagram] X: Requested content is not available, rate-limit reached or login required. Use --cookies" to
                    FriendlyError.PrivateOrLogin,
                "ERROR: [youtube] abc: Private video. Sign in if you've been granted access" to
                    FriendlyError.PrivateOrLogin,
                "ERROR: [youtube] abc: Video unavailable" to FriendlyError.Removed,
                "ERROR: [Instagram] DdvWZ0DsnN8: Unable to download webpage: The read operation timed out (caused by TransportError('The read operation timed out'))" to
                    FriendlyError.Network,
                "ERROR: Unsupported URL: https://example.com/page" to FriendlyError.Unsupported,
                "ERROR: [TikTok] 768: Unable to extract webpage video data" to FriendlyError.Extractor,
                "ERROR: [facebook] 1: No video formats found!" to FriendlyError.Extractor,
                "ERROR: [facebook] 28444030861885799: Cannot parse data; please report this issue" to
                    FriendlyError.Extractor,
                "something else" to FriendlyError.Unknown,
                null to FriendlyError.Unknown,
            )
            .forEach { (msg, expected) -> assertEquals(msg, expected, FriendlyError.of(msg)) }
    }
}
