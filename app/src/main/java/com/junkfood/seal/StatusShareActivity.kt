package com.junkfood.seal

import android.app.Activity
import android.os.Bundle
import com.junkfood.seal.util.FileUtil
import com.junkfood.seal.util.makeToast

/**
 * Notification "Share to Status" target. Not exported, so only our own PendingIntents can pass a
 * file path here (a receiver can't start activities on Android 12+).
 */
class StatusShareActivity : Activity() {
    companion object {
        const val EXTRA_FILE_PATH = "file_path"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(EXTRA_FILE_PATH)
        val share = FileUtil.createIntentForStatusSharing(path)
        if (share != null) {
            startActivity(share)
            FileUtil.deleteLaterIfAutoDelete(path)
        } else {
            makeToast(R.string.file_unavailable)
        }
        finish()
    }
}
