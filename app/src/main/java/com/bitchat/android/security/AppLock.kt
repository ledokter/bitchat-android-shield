package com.bitchat.android.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App lock with the platform BiometricPrompt (fingerprint, face or the screen-lock code) — no extra
 * dependency. Locks on cold start and after [RELOCK_AFTER_MS] (5 min) in the background. A device without
 * a secure screen lock cannot be locked, so the app stays open there.
 */
object AppLock {
    private const val RELOCK_AFTER_MS = 5 * 60_000L
    const val REQUEST_DEVICE_CREDENTIAL = 0xC4AB

    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var backgroundSince = 0L
    private var prompting = false

    fun canLock(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    /** Activity.onStart: relock if needed, then ask to unlock. */
    fun onStart(activity: Activity) {
        if (!SecurityPreferences.appLock.value || !canLock(activity)) {
            _locked.value = false
            return
        }
        if (backgroundSince > 0 && SystemClock.elapsedRealtime() - backgroundSince > RELOCK_AFTER_MS) {
            _locked.value = true
        }
        backgroundSince = 0
        if (_locked.value) authenticate(activity)
    }

    /** Activity.onStop. */
    fun onStop() {
        backgroundSince = SystemClock.elapsedRealtime()
    }

    fun unlock() {
        _locked.value = false
    }

    fun authenticate(activity: Activity) {
        if (prompting) return
        if (!SecurityPreferences.appLock.value || !canLock(activity)) {
            _locked.value = false
            return
        }
        prompting = true
        val title = activity.getString(com.bitchat.android.R.string.app_lock_prompt_title)
        try {
            prompt(activity, title)
        } catch (e: Exception) {
            // biometric service refused (OEM quirk, missing hardware…): use the screen-lock code
            android.util.Log.w("AppLock", "BiometricPrompt failed, falling back: ${e.message}")
            confirmCredential(activity, title)
        }
    }

    private fun prompt(activity: Activity, title: String) {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                BiometricPrompt.Builder(activity)
                    .setTitle(title)
                    .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_WEAK or
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    )
                    .build()
                    .authenticate(CancellationSignal(), activity.mainExecutor, callback())
            }
            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
                @Suppress("DEPRECATION")
                BiometricPrompt.Builder(activity)
                    .setTitle(title)
                    .setDeviceCredentialAllowed(true)
                    .build()
                    .authenticate(CancellationSignal(), activity.mainExecutor, callback())
            }
            else -> confirmCredential(activity, title)
        }
    }

    /** Screen-lock credential screen (Android 8-9, or fallback); result in onActivityResult. */
    private fun confirmCredential(activity: Activity, title: String) {
        @Suppress("DEPRECATION")
        val intent = activity.getSystemService(KeyguardManager::class.java)
            ?.createConfirmDeviceCredentialIntent(title, null)
        if (intent == null) {
            prompting = false
            _locked.value = false
        } else {
            @Suppress("DEPRECATION")
            activity.startActivityForResult(intent, REQUEST_DEVICE_CREDENTIAL)
        }
    }

    /** Result of the Android 8-9 credential screen. */
    fun onCredentialResult(ok: Boolean) {
        prompting = false
        if (ok) _locked.value = false
    }

    private fun callback() = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
            prompting = false
            _locked.value = false
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
            // cancelled or too many attempts: stay locked, the lock screen offers to retry
            prompting = false
        }
    }
}
