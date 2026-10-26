package com.bitchat.android.security

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App lock with chatbit's own PIN only. Fingerprint and face unlock are deliberately not offered:
 * someone can force a finger or a face onto the phone, which would make the duress PIN useless.
 * Locks on cold start and after [RELOCK_AFTER_MS] (5 min) in the background; when no PIN exists
 * yet, the lock screen asks to create one.
 */
object AppLock {
    private const val RELOCK_AFTER_MS = 5 * 60_000L

    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var backgroundSince = 0L

    /** Activity.onStart: relock after a long background stay. */
    fun onStart(context: Context) {
        if (!SecurityPreferences.appLock.value || SecurityPreferences.isTestLab(context)) {
            _locked.value = false
            return
        }
        if (backgroundSince > 0 && SystemClock.elapsedRealtime() - backgroundSince > RELOCK_AFTER_MS) {
            _locked.value = true
        }
        backgroundSince = 0
    }

    /** Activity.onStop. */
    fun onStop() {
        backgroundSince = SystemClock.elapsedRealtime()
    }

    fun unlock() {
        _locked.value = false
    }
}
