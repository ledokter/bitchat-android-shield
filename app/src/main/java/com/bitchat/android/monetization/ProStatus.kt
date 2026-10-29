package com.bitchat.android.monetization

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * chatbit Pro through Google Play Billing (the only payment of the Play Store version).
 * Pro removes the video ads in front of the anti-spam and advanced settings.
 *
 * Products to create in the Play Console:
 *  - subscription [SUB_PRO], base plans "monthly" (2.99 EUR) and "yearly" (24.99 EUR)
 *  - subscription [SUB_FOUNDER], base plan "yearly" (14.99 EUR, first 1,000 subscribers)
 *  - one-time product [EVENT_PASS] (19 EUR, Pro for 7 days)
 * Nothing is sent anywhere but Google Play; chatbit keeps no account.
 */
object ProStatus {
    private const val TAG = "ProStatus"
    const val SUB_PRO = "chatbit_pro"
    const val SUB_FOUNDER = "chatbit_founder"
    const val EVENT_PASS = "chatbit_event_pass"
    private const val EVENT_PASS_MS = 7L * 24 * 3600 * 1000

    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    /** Product details from Play (localized prices); empty until Play answers or if not on sale yet. */
    private val _products = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val products: StateFlow<Map<String, ProductDetails>> = _products.asStateFlow()

    /** The purchase that makes this account Pro (product id, purchase token), for the server pass. */
    @Volatile var activePurchase: Pair<String, String>? = null
        private set

    private var client: BillingClient? = null

    fun init(context: Context) {
        if (client != null) return
        client = BillingClient.newBuilder(context.applicationContext)
            .setListener { result, purchases -> if (result.responseCode == BillingClient.BillingResponseCode.OK) handle(purchases.orEmpty()) }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
        connect()
    }

    private fun connect() {
        client?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.i(TAG, "billing unavailable: ${result.responseCode}")
                    return
                }
                refresh()
                loadProducts()
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    /** Re-reads what this Play account owns (subscriptions and the event pass). */
    fun refresh() {
        val c = client ?: return
        val owned = mutableListOf<Purchase>()
        c.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { r1, subs ->
            if (r1.responseCode == BillingClient.BillingResponseCode.OK) owned += subs
            c.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { r2, inapp ->
                if (r2.responseCode == BillingClient.BillingResponseCode.OK) owned += inapp
                handle(owned, reset = true)
            }
        }
    }

    private fun loadProducts() {
        val c = client ?: return
        fun product(id: String, type: String) = QueryProductDetailsParams.Product.newBuilder().setProductId(id).setProductType(type).build()
        val subs = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product(SUB_PRO, BillingClient.ProductType.SUBS), product(SUB_FOUNDER, BillingClient.ProductType.SUBS)))
            .build()
        val inapp = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product(EVENT_PASS, BillingClient.ProductType.INAPP)))
            .build()
        for (params in listOf(subs, inapp)) {
            c.queryProductDetailsAsync(params) { result, details ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _products.value = _products.value + details.productDetailsList.associateBy { it.productId }
                }
            }
        }
    }

    /** [reset]: [purchases] is everything the account owns, not just a new purchase. */
    private fun handle(purchases: List<Purchase>, reset: Boolean = false) {
        val now = System.currentTimeMillis()
        var pro = _isPro.value && !reset
        if (reset) activePurchase = null
        for (p in purchases) {
            if (p.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            val pass = EVENT_PASS in p.products
            if (!pass || now - p.purchaseTime < EVENT_PASS_MS) {
                pro = true
                activePurchase = p.products.first() to p.purchaseToken
            }
            if (!p.isAcknowledged) {
                client?.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { }
            }
        }
        // a newly bought (or lapsed) Pro must reach the server: take a new pass
        if (pro != _isPro.value) com.bitchat.android.server.ChatbitServer.invalidate()
        _isPro.value = pro
    }

    /**
     * Opens the Play purchase screen. [basePlan] picks "monthly" or "yearly" for subscriptions.
     * Returns false when the product is not on sale (yet) in Play.
     */
    fun buy(activity: Activity, productId: String, basePlan: String? = null): Boolean {
        val c = client ?: return false
        val details = _products.value[productId] ?: return false
        val params = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details)
        details.subscriptionOfferDetails?.let { offers ->
            val offer = offers.firstOrNull { basePlan == null || it.basePlanId == basePlan } ?: return false
            params.setOfferToken(offer.offerToken)
        }
        val flow = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params.build())).build()
        return c.launchBillingFlow(activity, flow).responseCode == BillingClient.BillingResponseCode.OK
    }

    /** Price shown by Play for a plan, in the buyer's currency; null when Play has not answered. */
    fun price(productId: String, basePlan: String? = null): String? {
        val d = _products.value[productId] ?: return null
        d.oneTimePurchaseOfferDetails?.let { return it.formattedPrice }
        val offer = d.subscriptionOfferDetails?.firstOrNull { basePlan == null || it.basePlanId == basePlan } ?: return null
        return offer.pricingPhases.pricingPhaseList.lastOrNull()?.formattedPrice
    }
}
