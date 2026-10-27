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
 * unless something else was copied meanwhile. Android 10+ hides the clipboard from apps in the
 * background, so a clear that cannot check the clipboard is retried when chatbit regains focus.
 */
object SecureClipboard {
    private val handler = Handler(Looper.getMainLooper())
    private var pending: String? = null

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
        pending = null
        handler.postDelayed({
            if (!clearIfUnchanged(clipboard, text)) pending = text
        }, SecurityPreferences.CLIPBOARD_CLEAR_SECONDS * 1000)
    }

    /** Called when chatbit's window gains focus: finishes a clear that was blocked in the background. */
    fun onWindowFocused(context: Context) {
        val text = pending ?: return
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        if (clearIfUnchanged(clipboard, text)) pending = null
    }

    /** Returns false when the clipboard could not be read (app in the background, or already empty). */
    private fun clearIfUnchanged(clipboard: ClipboardManager, text: String): Boolean {
        val clip = runCatching { clipboard.primaryClip }.getOrNull() ?: return false
        if (clip.itemCount > 0 && clip.getItemAt(0)?.text?.toString() == text) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) clipboard.clearPrimaryClip()
            else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
        return true
    }
}
