package com.bitchat.android.monetization

import android.app.Activity
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.bitchat.android.BuildConfig

/**
 * Rewarded video in front of the anti-spam and advanced settings of the free version (AdMob,
 * personalized ads with the consent form required in Europe).
 *
 * Nothing ad-related runs at start-up: consent, SDK start and the video are only fetched when the
 * person taps "watch the video". The ad SDK uses its own connection, outside Tor; the gate says so.
 */
object RewardedAds {
    private const val TAG = "RewardedAds"

    enum class Result { REWARDED, CLOSED_EARLY, UNAVAILABLE }

    private var started = false

    /** Consent (if required), SDK start, load, show. [done] runs once, on the main thread. */
    fun show(activity: Activity, done: (Result) -> Unit) {
        val consent = UserMessagingPlatform.getConsentInformation(activity)
        consent.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) Log.i(TAG, "consent form: ${formError.message}")
                    if (consent.canRequestAds()) load(activity, done) else done(Result.UNAVAILABLE)
                }
            },
            { error ->
                Log.i(TAG, "consent update failed: ${error.message}")
                // offline or UMP unreachable: try anyway, AdMob serves only what consent allows
                load(activity, done)
            }
        )
    }

    private fun load(activity: Activity, done: (Result) -> Unit) {
        if (!started) {
            MobileAds.initialize(activity.applicationContext) {}
            started = true
        }
        RewardedAd.load(activity, BuildConfig.ADMOB_REWARDED_ID, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                var rewarded = false
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        done(if (rewarded) Result.REWARDED else Result.CLOSED_EARLY)
                    }

                    override fun onAdFailedToShowFullScreenContent(error: AdError) {
                        Log.i(TAG, "show failed: ${error.message}")
                        done(Result.UNAVAILABLE)
                    }
                }
                ad.show(activity) { rewarded = true }
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.i(TAG, "load failed: ${error.code}")
                done(Result.UNAVAILABLE)
            }
        })
    }
}
