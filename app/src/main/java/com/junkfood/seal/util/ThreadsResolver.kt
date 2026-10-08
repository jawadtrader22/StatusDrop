package com.junkfood.seal.util

import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * yt-dlp has no Threads extractor yet (yt-dlp#7523). A logged-out desktop page view embeds the
 * post JSON with direct MP4 links (`video_versions`), so we read that and hand yt-dlp the MP4.
 * ponytail: depends on Meta's page JSON; drop this once yt-dlp ships a Threads extractor.
 */
object ThreadsResolver {
    data class Media(val code: String, val videoUrl: String, val title: String?, val thumbnail: String?)

    private val client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private val json = Json { isLenient = true }
    private val scriptRegex = Regex("""<script[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)

    fun postCode(url: String): String? =
        Regex("""/post/([A-Za-z0-9_-]+)""").find(url)?.groupValues?.get(1)

    /** Null when the post has no public video (photo-only, private, removed, or page changed). */
    fun resolve(url: String): Media? {
        val (html, finalUrl) = runCatching { fetch(url) }.getOrNull() ?: return null
        // share links (threads.com/share/xyz) only reveal the post code after their redirect
        val code = postCode(finalUrl) ?: postCode(url) ?: return null
        return parse(html, code)
    }

    /** Page HTML and the URL it ended on after redirects. */
    private fun fetch(url: String): Pair<String, String>? {
        // the mobile page is a JS shell; the desktop one carries the post JSON
        val request =
            Request.Builder()
                .url(url)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
                )
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Dest", "document")
                .build()
        return client.newCall(request).execute().use {
            if (it.isSuccessful) it.body.string() to it.request.url.toString() else null
        }
    }

    fun parse(html: String, code: String): Media? {
        // the page repeats the post: one copy may have the video but no caption, so merge them
        val posts =
            scriptRegex
                .findAll(html)
                .map { it.groupValues[1] }
                .filter { "\"code\":\"$code\"" in it }
                .mapNotNull { runCatching { json.parseToJsonElement(it) }.getOrNull() }
                .flatMap { collectPosts(it, code) }
                .toList()
        // own video first, then carousel items, then media embedded in the post
        // (text posts carry it under text_post_app_info.linked_inline_media)
        val video = posts.firstNotNullOfOrNull { firstVideoUrl(it) } ?: return null
        return Media(
            code = code,
            videoUrl = video,
            title =
                posts.firstNotNullOfOrNull { it.string("caption", "text") }
                    ?.lineSequence()
                    ?.firstOrNull { line -> line.isNotBlank() }
                    ?.take(80),
            thumbnail = posts.firstNotNullOfOrNull { firstThumbnail(it) },
        )
    }

    private fun collectPosts(element: JsonElement, code: String): List<JsonObject> =
        when (element) {
            is JsonObject ->
                (if (element.string("code") == code) listOf(element) else emptyList()) +
                    element.values.flatMap { collectPosts(it, code) }
            is JsonArray -> element.flatMap { collectPosts(it, code) }
            else -> emptyList()
        }

    // same search for the cover image, so text posts with embedded videos get one too
    private fun firstThumbnail(element: JsonElement): String? =
        when (element) {
            is JsonObject ->
                ((element["image_versions2"] as? JsonObject)?.get("candidates") as? JsonArray)
                    ?.firstOrNull()
                    ?.let { (it as? JsonObject)?.string("url") }
                    ?: element.values.firstNotNullOfOrNull { firstThumbnail(it) }
            is JsonArray -> element.firstNotNullOfOrNull { firstThumbnail(it) }
            else -> null
        }

    // depth-first; video_versions is ordered best first
    private fun firstVideoUrl(element: JsonElement): String? =
        when (element) {
            is JsonObject ->
                (element["video_versions"] as? JsonArray)?.firstOrNull()?.let {
                    (it as? JsonObject)?.string("url")
                } ?: element.values.firstNotNullOfOrNull { firstVideoUrl(it) }
            is JsonArray -> element.firstNotNullOfOrNull { firstVideoUrl(it) }
            else -> null
        }

    private fun JsonObject.string(vararg path: String): String? {
        var node: JsonElement = this
        for (key in path) node = (node as? JsonObject)?.get(key) ?: return null
        return (node as? JsonPrimitive)?.contentOrNull
    }
}
