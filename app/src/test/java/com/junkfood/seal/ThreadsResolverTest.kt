package com.junkfood.seal

import com.junkfood.seal.util.ThreadsResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThreadsResolverTest {
    private fun page(post: String) =
        """<html><script type="application/json">{"other":1}</script>""" +
            """<script type="application/json" data-sjs>{"require":[["x",{"result":{"data":{"edges":[{"node":{"thread_items":[{"post":$post}]}}]}}}]]}</script></html>"""

    @Test
    fun extractsPostCode() {
        assertEquals("DeOD4p1jVyE", ThreadsResolver.postCode("https://www.threads.com/@a/post/DeOD4p1jVyE/media"))
        assertEquals("Ab_c-1", ThreadsResolver.postCode("https://www.threads.net/@a/post/Ab_c-1?x=1"))
        assertNull(ThreadsResolver.postCode("https://www.threads.com/@a"))
    }

    @Test
    fun picksBestVideoCaptionAndThumbnail() {
        val media =
            ThreadsResolver.parse(
                page(
                    """{"code":"ABC","caption":{"text":"Line one\nline two"},""" +
                        """"image_versions2":{"candidates":[{"url":"https:\/\/cdn\/t.jpg"}]},""" +
                        """"video_versions":[{"type":101,"url":"https:\/\/cdn\/hd.mp4"},{"type":102,"url":"https:\/\/cdn\/sd.mp4"}]}"""
                ),
                "ABC",
            )!!
        assertEquals("https://cdn/hd.mp4", media.videoUrl)
        assertEquals("Line one", media.title)
        assertEquals("https://cdn/t.jpg", media.thumbnail)
    }

    @Test
    fun mergesCaptionFromAnotherCopyOfThePost() {
        val html =
            page("""{"code":"ABC","video_versions":[{"type":101,"url":"https:\/\/cdn\/v.mp4"}],"caption":null}""")
                .replace("</html>", "") +
                """<script type="application/json">{"post":{"code":"ABC","caption":{"text":"Real"},""" +
                """"video_versions":[{"type":101,"url":"https:\/\/cdn\/v.mp4"}]}}</script></html>"""
        val media = ThreadsResolver.parse(html, "ABC")!!
        assertEquals("Real", media.title)
        assertEquals("https://cdn/v.mp4", media.videoUrl)
    }

    @Test
    fun usesFirstVideoInCarousel() {
        val media =
            ThreadsResolver.parse(
                page(
                    """{"code":"CAR","carousel_media":[{"video_versions":null},""" +
                        """{"video_versions":[{"type":101,"url":"https:\/\/cdn\/2.mp4"}]}]}"""
                ),
                "CAR",
            )
        assertEquals("https://cdn/2.mp4", media?.videoUrl)
    }

    @Test
    fun photoOnlyOrOtherPostGivesNull() {
        assertNull(ThreadsResolver.parse(page("""{"code":"PIC","video_versions":null}"""), "PIC"))
        assertNull(ThreadsResolver.parse(page("""{"code":"ABC","video_versions":[{"url":"x"}]}"""), "ZZZ"))
    }
}
