package com.battlesbudz.dinnerbot.feature.premium

import android.app.Activity
import com.revenuecat.purchases.Package

/**
 * The billing seam for Dinner Bot Premium (Phase C3).
 *
 * Premium status has exactly one source of truth: the "premium"
 * entitlement in RevenueCat. The app caches the last known status on
 * device and consults the cache for every gate, so a store outage can
 * never hard-lock a paying user out of premium, and no free feature
 * ever depends on billing being reachable (see PremiumRules in
 * core-model for the fail-soft resolution rule).
 *
 * Billing is configured through a public API key supplied at build
 * time (see this module's build file). Until that key exists, billing
 * is UNCONFIGURED and that state is fully supported: [isConfigured] is
 * false, the free tier works exactly as it does without billing, and
 * purchase actions report [PurchaseResult.Unavailable] so the paywall
 * can say honestly that purchases are not available yet.
 *
 * Store products (created in Play Console and attached to the
 * "premium" entitlement in the RevenueCat dashboard — Battles-needed
 * launch items, listed on issue #40):
 * - [PRODUCT_MONTHLY]: $4.99/month subscription
 * - [PRODUCT_ANNUAL]: $39.99/year subscription; the 14-day free trial
 *   is an offer on this product, configured in Play Console
 * - [PRODUCT_LIFETIME]: $79.99 one-time purchase, configured as a
 *   NON-CONSUMABLE in RevenueCat (a consumable lifetime purchase
 *   could not be restored)
 */
const val ENTITLEMENT_PREMIUM = "premium"
const val PRODUCT_MONTHLY = "dinnerbot_premium_monthly"
const val PRODUCT_ANNUAL = "dinnerbot_premium_annual"
const val PRODUCT_LIFETIME = "dinnerbot_premium_lifetime"

/** Which of the three decided offers a paywall product is. */
enum class PaywallProductKind { MONTHLY, ANNUAL, LIFETIME }

/**
 * One offer as the paywall presents it. When the product came from the
 * store, [priceFormatted] is the store's own price string for this
 * user's storefront — never a hard-coded string. [trialLabel] is set
 * only when the store data for the annual product actually carries a
 * free trial phase, so the paywall never promises a trial the store
 * would not honour. [rcPackage] is the RevenueCat package to purchase;
 * it stays inside this module so the rest of the app never sees
 * RevenueCat types.
 */
data class PaywallProduct(
    val kind: PaywallProductKind,
    val productId: String,
    val title: String,
    val priceFormatted: String,
    val periodLabel: String,
    val trialLabel: String?,
    val rcPackage: Package?,
)

/** How a purchase or restore attempt ended. */
sealed interface PurchaseResult {
    /** The store confirms the premium entitlement is now active. */
    data object Purchased : PurchaseResult

    /** The user backed out of the store sheet. Not an error. */
    data object Cancelled : PurchaseResult

    /** The attempt failed; [message] is fit to show the user. */
    data class Failed(val message: String) : PurchaseResult

    /** Billing is unconfigured or the product is not purchasable here. */
    data object Unavailable : PurchaseResult
}

/** Everything the app needs from the billing provider. */
interface PremiumBilling {

    /** True when an API key was supplied at build time. */
    val isConfigured: Boolean

    /**
     * Configure the SDK once and subscribe to entitlement changes.
     * [onPremiumStatus] fires with the entitlement's active state
     * whenever RevenueCat reports it — the initial fetch, purchases,
     * restores, renewals and revocations. When billing is unconfigured
     * this does nothing and the callback never fires.
     */
    fun start(onPremiumStatus: (Boolean) -> Unit)

    /**
     * A fresh entitlement read, or null when it cannot be determined
     * (unconfigured, offline, store error). Null means "keep the
     * cached status", never "treat as free".
     */
    suspend fun refreshPremium(): Boolean?

    /**
     * The current offering's products in monthly, annual, lifetime
     * order. Empty when unconfigured or when the offering cannot be
     * loaded; the paywall falls back to its honest static presentation.
     */
    suspend fun loadProducts(): List<PaywallProduct>

    /** Run the store purchase flow for [product] from [activity]. */
    suspend fun purchase(activity: Activity, product: PaywallProduct): PurchaseResult

    /** Restore previous purchases; [PurchaseResult.Purchased] when premium is active afterwards. */
    suspend fun restorePurchases(): PurchaseResult
}
