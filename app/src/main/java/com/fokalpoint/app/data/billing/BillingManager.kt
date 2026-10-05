package com.fokalpoint.app.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Google Play Billing for the "creator_pro" subscription.
 *
 * Play Console setup: create subscription product `creator_pro` with base plans
 * `monthly` and `yearly`. Prices shown in the app always come from Play.
 */
class BillingManager(context: Context) : PurchasesUpdatedListener {

    data class Plan(
        val basePlanId: String,
        val formattedPrice: String,
        val billingPeriod: String, // ISO-8601, e.g. P1M / P1Y
        val offerToken: String,
        val freeTrialPeriod: String?,
        internal val details: ProductDetails
    ) {
        val isYearly: Boolean get() = billingPeriod.endsWith("Y")
    }

    sealed class Event {
        data class Purchased(val purchase: Purchase) : Event()
        object Cancelled : Event()
        data class Failed(val message: String) : Event()
    }

    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private val _plans = MutableStateFlow<List<Plan>>(emptyList())
    val plans: StateFlow<List<Plan>> = _plans.asStateFlow()

    private val _available = MutableStateFlow<Boolean?>(null)
    /** null = not checked yet; false = Play Billing unavailable (e.g. sideloaded / emulator without Play). */
    val available: StateFlow<Boolean?> = _available.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    suspend fun connect(): Boolean {
        if (client.isReady) return true
        val result = CompletableDeferred<Boolean>()
        try {
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    result.complete(billingResult.responseCode == BillingClient.BillingResponseCode.OK)
                }
                override fun onBillingServiceDisconnected() {
                    result.complete(false)
                }
            })
        } catch (e: Exception) {
            result.complete(false)
        }
        val ok = withTimeoutOrNull(8_000) { result.await() } ?: false
        _available.value = ok
        return ok
    }

    suspend fun loadPlans(): List<Plan> {
        if (!connect()) return emptyList()
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            )
        ).build()
        val details = client.queryProductDetails(params).productDetailsList?.firstOrNull() ?: return emptyList()
        val plans = details.subscriptionOfferDetails.orEmpty()
            .groupBy { it.basePlanId }
            .mapNotNull { (basePlan, offers) ->
                // Prefer an introductory offer (e.g. free trial) when Play makes one available.
                val offer = offers.firstOrNull { it.offerId != null } ?: offers.first()
                val phases = offer.pricingPhases.pricingPhaseList
                val recurring = phases.lastOrNull() ?: return@mapNotNull null
                Plan(
                    basePlanId = basePlan,
                    formattedPrice = recurring.formattedPrice,
                    billingPeriod = recurring.billingPeriod,
                    offerToken = offer.offerToken,
                    freeTrialPeriod = phases.firstOrNull { it.priceAmountMicros == 0L }?.billingPeriod,
                    details = details
                )
            }
            .sortedBy { if (it.isYearly) 1 else 0 }
        _plans.value = plans
        return plans
    }

    /**
     * [accountId] is the FokalPoint user id (a random UUID, no personal data). The
     * verify-play-subscription function rejects purchases made for another account.
     */
    fun launchPurchase(activity: Activity, plan: Plan, accountId: String): Boolean {
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(plan.details)
                        .setOfferToken(plan.offerToken)
                        .build()
                )
            )
            .setObfuscatedAccountId(accountId)
            .build()
        return client.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    /** Currently owned Creator Pro purchases (for restore and renewal re-verification). */
    suspend fun ownedProPurchases(): List<Purchase> {
        if (!connect()) return emptyList()
        val result = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        )
        return result.purchasesList.filter {
            PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED
        }
    }

    suspend fun acknowledge(purchase: Purchase): Boolean {
        if (purchase.isAcknowledged) return true
        val result = client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        )
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.orEmpty()
                .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                .forEach { _events.tryEmit(Event.Purchased(it)) }
            BillingClient.BillingResponseCode.USER_CANCELED -> _events.tryEmit(Event.Cancelled)
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
                _events.tryEmit(Event.Failed("You already have Creator Pro. Use \"Restore purchase\"."))
            else -> _events.tryEmit(Event.Failed(result.debugMessage.ifBlank { "Purchase failed (${result.responseCode})" }))
        }
    }

    fun close() = client.endConnection()

    companion object {
        const val PRODUCT_ID = "creator_pro"
    }
}
