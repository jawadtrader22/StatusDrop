package com.junkfood.seal.util

import java.net.URI

/** Platform guessed from a link's host, shown before yt-dlp fetches anything. */
enum class Platform(val label: String, val color: Long, private val hosts: List<String>) {
    Facebook("Facebook", 0xFF1877F2, listOf("facebook.com", "fb.watch", "fb.com")),
    Instagram("Instagram", 0xFFE1306C, listOf("instagram.com", "instagr.am")),
    TikTok("TikTok", 0xFF25F4EE, listOf("tiktok.com")),
    YouTube("YouTube", 0xFFFF0000, listOf("youtube.com", "youtu.be")),
    X("X / Twitter", 0xFF8899A6, listOf("x.com", "twitter.com")),
    Threads("Threads", 0xFF9E9E9E, listOf("threads.net", "threads.com")),
    Snapchat("Snapchat", 0xFFFFFC00, listOf("snapchat.com")),
    Pinterest("Pinterest", 0xFFE60023, listOf("pinterest.com", "pin.it")),
    Reddit("Reddit", 0xFFFF4500, listOf("reddit.com", "redd.it")),
    Likee("Likee", 0xFFFF3D7F, listOf("likee.video", "like.video")),
    Vimeo("Vimeo", 0xFF1AB7EA, listOf("vimeo.com")),
    Dailymotion("Dailymotion", 0xFF0066DC, listOf("dailymotion.com", "dai.ly")),
    Other("1000+ sites", 0xFF7E57C2, emptyList());

    companion object {
        fun of(url: String): Platform {
            val host =
                runCatching { URI(url.trim()).host }.getOrNull()?.lowercase()?.removePrefix("www.")
                    ?: return Other
            return entries.firstOrNull { p -> p.hosts.any { host == it || host.endsWith(".$it") } }
                ?: Other
        }
    }
}
