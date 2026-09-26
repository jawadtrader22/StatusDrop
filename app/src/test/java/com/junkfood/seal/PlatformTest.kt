package com.junkfood.seal

import com.junkfood.seal.util.Platform
import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformTest {
    @Test
    fun detectsPlatformFromHost() {
        mapOf(
                "https://www.facebook.com/reel/1248341667281091" to Platform.Facebook,
                "https://m.facebook.com/watch?v=1" to Platform.Facebook,
                "https://fb.watch/abc/" to Platform.Facebook,
                "https://www.instagram.com/reel/xyz/" to Platform.Instagram,
                "https://vm.tiktok.com/ZM123/" to Platform.TikTok,
                "https://youtu.be/dQw4w9WgXcQ" to Platform.YouTube,
                "https://www.youtube.com/shorts/abc" to Platform.YouTube,
                "https://x.com/user/status/1" to Platform.X,
                "https://pin.it/abc" to Platform.Pinterest,
                "https://example.com/video" to Platform.Other,
                "https://notfacebook.com/x" to Platform.Other,
                "not a url" to Platform.Other,
            )
            .forEach { (url, expected) -> assertEquals(url, expected, Platform.of(url)) }
    }
}
