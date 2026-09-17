package com.mwilky.hilight.plus

import android.app.Activity
import android.app.Application
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Trial-then-one-off-purchase licensing.
 *
 * The trial clock starts the first time the daemon connects, because the app can't do anything
 * before then. Its start time lives in Settings.Global (written by the shell-UID daemon), so it
 * survives clearing app data and reinstalling; [AppStore] only holds a local copy so the UI can
 * render before Shizuku is up. The purchase is a single non-consumable Play product.
 */
class Licensing(
    app: Application,
    private val store: AppStore,
    private val shizuku: ShizukuBridge
) {

    data class Status(
        val purchased: Boolean,
        val trialStartMillis: Long?,
        val now: Long,
        val priceText: String?
    ) {
        val trialEndMillis: Long? get() = trialStartMillis?.plus(TRIAL_DURATION_MS)

        /** Whole days left, rounded up so the last partial day still reads as "1 day". */
        val trialDaysLeft: Int? get() = trialEndMillis?.let { end ->
            ((end - now + DAY_MS - 1) / DAY_MS).toInt().coerceAtLeast(0)
        }

        val trialExpired: Boolean get() = trialEndMillis?.let { now >= it } ?: false

        /** No trial start yet means the daemon has never connected, so there's nothing to gate. */
        val entitled: Boolean get() = purchased || !trialExpired
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val priceText = MutableStateFlow<String?>(null)
    private var productDetails: ProductDetails? = null

    // Re-evaluates once a minute so an expiring trial is noticed while the app is running.
    private val clock = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(CLOCK_TICK_MS)
        }
    }

    val status: StateFlow<Status> = combine(
        store.isPurchased,
        store.trialStartMillis,
        clock,
        priceText
    ) { purchased, trialStart, now, price -> Status(purchased, trialStart, now, price) }
        .stateIn(
            scope,
            SharingStarted.Eagerly,
            Status(purchased = false, trialStartMillis = null, now = System.currentTimeMillis(), priceText = null)
        )

    val isEntitled: StateFlow<Boolean> = status.map { it.entitled }
        .stateIn(scope, SharingStarted.Eagerly, true)

    private val billing: BillingClient = BillingClient.newBuilder(app)
        .setListener { result, purchases -> onPurchasesUpdated(result, purchases) }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    init {
        scope.launch {
            shizuku.state.collect { state ->
                if (state == ShizukuBridge.State.CONNECTED) syncTrialStart()
            }
        }
        billing.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
                    return
                }
                refreshPurchases()
                queryProductDetails(onReady = null)
            }

            override fun onBillingServiceDisconnected() {
                // enableAutoServiceReconnection() handles retrying.
            }
        })
    }

    /**
     * Settings.Global is the source of truth. Adopt it if present, otherwise start the trial now
     * (or from the local copy, if the daemon was somehow never able to record it before).
     */
    private suspend fun syncTrialStart() {
        val remote = withContext(Dispatchers.IO) { shizuku.getGlobalString(GLOBAL_TRIAL_START) }?.toLongOrNull()
        if (remote != null) {
            store.setTrialStartMillis(remote)
            return
        }
        val start = store.trialStartMillis.first() ?: System.currentTimeMillis()
        val written = withContext(Dispatchers.IO) { shizuku.putGlobalString(GLOBAL_TRIAL_START, start.toString()) }
        if (!written) Log.w(TAG, "Couldn't record trial start in Settings.Global")
        store.setTrialStartMillis(start)
    }

    // --- Play Billing ---

    /** Re-reads Play's local purchase cache. Cheap; call on resume so refunds and restores land. */
    fun refreshPurchases() {
        if (!billing.isReady) return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billing.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                applyPurchases(purchases)
            }
        }
    }

    /** Starts the Play purchase sheet. Fetches product details first if they haven't loaded yet. */
    fun purchase(activity: Activity) {
        val details = productDetails
        if (details != null) {
            launchBillingFlow(activity, details)
        } else {
            queryProductDetails(onReady = { launchBillingFlow(activity, it) })
        }
    }

    private fun launchBillingFlow(activity: Activity, details: ProductDetails) {
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .build()
        val result = billing.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "launchBillingFlow failed: ${result.debugMessage}")
        }
    }

    private fun queryProductDetails(onReady: ((ProductDetails) -> Unit)?) {
        if (!billing.isReady) return
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        billing.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "queryProductDetails failed: ${result.debugMessage}")
                return@queryProductDetailsAsync
            }
            val details = queryResult.productDetailsList.firstOrNull { it.productId == PRODUCT_ID } ?: return@queryProductDetailsAsync
            productDetails = details
            priceText.value = details.oneTimePurchaseOfferDetails?.formattedPrice
            onReady?.let { scope.launch { it(details) } }
        }
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases?.let { applyPurchases(it, fromFlow = true) }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refreshPurchases()
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> Log.w(TAG, "Purchase update failed: ${result.debugMessage}")
        }
    }

    /**
     * [fromFlow] purchases come from the purchase sheet and only ever add to what's owned; a
     * full [refreshPurchases] result is authoritative and can also revoke (refund).
     */
    private fun applyPurchases(purchases: List<Purchase>, fromFlow: Boolean = false) {
        var owned = false
        for (purchase in purchases) {
            if (PRODUCT_ID !in purchase.products) continue
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            owned = true
            if (!purchase.isAcknowledged) acknowledge(purchase)
        }
        if (owned || !fromFlow) {
            scope.launch { store.setPurchased(owned) }
        }
    }

    private fun acknowledge(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billing.acknowledgePurchase(params) { result ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "acknowledgePurchase failed: ${result.debugMessage}")
            }
        }
    }

    companion object {
        private const val TAG = "HiLightLicensing"
        const val PRODUCT_ID = "pro_unlock"
        const val TRIAL_DAYS = 7
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private const val TRIAL_DURATION_MS = TRIAL_DAYS * DAY_MS
        private const val CLOCK_TICK_MS = 60_000L
        private const val GLOBAL_TRIAL_START = "hilight_plus_trial_start"
    }
}
