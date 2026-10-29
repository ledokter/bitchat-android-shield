package com.bitchat.android.server

import android.content.Context
import android.util.Log
import com.bitchat.android.monetization.ProStatus
import com.bitchat.android.net.OkHttpProvider
import com.google.android.gms.tasks.Task
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * chatbit services (zone directory, private rooms relay), reached only through Tor at an onion
 * address. They answer only to a pass: the server hands one out after Google Play Integrity
 * confirms this is the genuine chatbit app from Play (rebuilt copies get none), with tier "pro"
 * when a Play purchase is shown too. The pass names no device and no account; it lives in memory.
 */
object ChatbitServer {
    private const val TAG = "ChatbitServer"
    const val ONION = "w3nmrc4p6gzqhrrkiisv5aajo3syohmxtmeshgrbvp6uxykbcbuggbad.onion"
    const val BASE_URL = "http://$ONION"
    const val RELAY_URL = "ws://$ONION/v1/relay"
    /** Google Cloud project linked to the app in the Play Console (Play Integrity). */
    private const val CLOUD_PROJECT_NUMBER = 278701647823L
    private const val RENEW_BEFORE_S = 600

    class Pass(val token: String, val tier: String, val expS: Long)

    private val lock = Mutex()
    @Volatile private var current: Pass? = null
    private var provider: StandardIntegrityTokenProvider? = null

    /** Tier of the last pass ("free" or "pro"), null before the first one. */
    private val _tier = MutableStateFlow<String?>(null)
    val tier: StateFlow<String?> = _tier.asStateFlow()

    /** A valid pass, fetched or renewed when needed; null when Play or the server cannot give one. */
    suspend fun pass(context: Context): Pass? = lock.withLock {
        val now = System.currentTimeMillis() / 1000
        current?.takeIf { it.expS - RENEW_BEFORE_S > now }?.let { return it }
        withContext(Dispatchers.IO) {
            runCatching { fetchPass(context.applicationContext) }
                .onFailure { Log.w(TAG, "no pass: ${it.message}") }
                .getOrNull()
        }?.also {
            current = it
            _tier.value = it.tier
        }
    }

    /** Pro purchase changed (bought, expired): the next request takes a new pass. */
    fun invalidate() {
        current = null
    }

    fun wipe() {
        current = null
        _tier.value = null
    }

    private suspend fun fetchPass(context: Context): Pass {
        val http = OkHttpProvider.httpClient()
        val challenge = http.newCall(Request.Builder().url("$BASE_URL/v1/challenge").build()).execute().use {
            check(it.isSuccessful) { "challenge ${it.code}" }
            JSONObject(it.body!!.string()).getString("challenge")
        }
        val token = integrityToken(context, challenge)
        val body = JSONObject().put("challenge", challenge).put("integrity_token", token)
        ProStatus.activePurchase?.let { (product, purchase) ->
            body.put("product_id", product).put("purchase_token", purchase)
        }
        val request = Request.Builder().url("$BASE_URL/v1/pass")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        return http.newCall(request).execute().use {
            check(it.isSuccessful) { "pass ${it.code}" }
            val o = JSONObject(it.body!!.string())
            Pass(o.getString("pass"), o.getString("tier"), o.getLong("exp"))
        }
    }

    private suspend fun integrityToken(context: Context, challenge: String): String {
        val p = provider ?: IntegrityManagerFactory.createStandard(context)
            .prepareIntegrityToken(PrepareIntegrityTokenRequest.builder().setCloudProjectNumber(CLOUD_PROJECT_NUMBER).build())
            .await().also { provider = it }
        return try {
            p.request(StandardIntegrityTokenRequest.builder().setRequestHash(challenge).build()).await().token()
        } catch (e: Exception) {
            provider = null // a stale provider fails; prepared again next time
            throw e
        }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
    }
}
