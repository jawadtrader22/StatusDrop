package com.junkfood.seal

import com.junkfood.seal.ui.component.ytDlpProgressDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadProgressTest {
    @Test
    fun parsesYtDlpProgressLine() {
        assertEquals(
            "250.21MiB · 2.36MiB/s · 01:02 left",
            ytDlpProgressDetails(
                "[download]  46.3% of ~ 250.21MiB at  2.36MiB/s ETA 01:02 (frag 20/58)"
            ),
        )
        assertEquals(
            "1.35MiB · 820.00KiB/s · 00:01 left",
            ytDlpProgressDetails("[download]  12.0% of 1.35MiB at 820.00KiB/s ETA 00:01"),
        )
        assertNull(ytDlpProgressDetails("[download] Destination: video.mp4"))
    }
}
