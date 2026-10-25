package com.bitchat.android.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * Copies text marked as sensitive (hidden from the Android 13+ clipboard preview) and, when the
 * clipboard-clear protection is on, erases it after [SecurityPreferences.CLIPBOARD_CLEAR_SECONDS]
 * unless something else was copied meanwhile.
 */
object SecureClipboard {
    private val handler = Handler(Looper.getMainLooper())

    fun copy(context: Context, label: String, text: String, sensitive: Boolean = true) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText(label, text)
        if (sensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = android.os.PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard.setPrimaryClip(clip)
        if (!SecurityPreferences.clipboardClear.value) return
        handler.postDelayed({
            val current = runCatching { clipboard.primaryClip?.getItemAt(0)?.text?.toString() }.getOrNull()
            if (current == text) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) clipboard.clearPrimaryClip()
                else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }, SecurityPreferences.CLIPBOARD_CLEAR_SECONDS * 1000)
    }
}
