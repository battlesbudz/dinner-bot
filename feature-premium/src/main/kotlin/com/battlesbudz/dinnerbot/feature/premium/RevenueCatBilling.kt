package com.battlesbudz.dinnerbot.feature.premium

import android.app.Activity
import android.content.Context
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PackageType
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.getCustomerInfoWith
import com.revenuecat.purchases.getOfferingsWith
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.purchaseWith
import com.revenuecat.purchases.restorePurchasesWith
import com.revenuecat.purchases.models.StoreProduct
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * The RevenueCat-backed [PremiumBilling] (Phase C3).
 *
 * Everything RevenueCat lives behind this class: the rest of the app
 * sees only the [PremiumBilling] seam and the on-device cache. Every
 * call is guarded so an unconfigured build (no API key yet) or an
 * unreachable store degrades to "unknown", and the caller keeps the
 * cached status — billing can never break the free tier.
 */
class RevenueCatBilling(context: Context) : PremiumBilling {

    private val appContext: Context = context.applicationContext

    override val isConfigured: Boolean = BuildConfig.REVENUECAT_API_KEY.isNotBlank()

    @Volatile
    private var statusListener: ((Boolean) -> Unit)? = null

    override fun start(onPremiumStatus: (Boolean) -> Unit) {
        statusListener = onPremiumStatus
        if (!isConfigured) return
        runCatching {
            if (!Purchases.isConfigured) {
                Purchases.configure(
                    PurchasesConfiguration.Builder(appContext, BuildConfig.REVENUECAT_API_KEY)
                        .build(),
                )
            }
            Purchases.sharedInstance.updatedCustomerInfoListener =
                object : UpdatedCustomerInfoListener {
                    override fun onReceived(customerInfo: CustomerInfo) {
                        statusListener?.invoke(customerInfo.hasPremium())
                    }
                }
        }.onFailure { return }
        // The initial read: on success it corrects the cache; on any
        // failure it stays silent and the cached status stands.
        runCatching {
            Purchases.sharedInstance.getCustomerInfoWith(
                onError = { /* fail soft: keep the cached status */ },
                onSuccess = { info -> statusListener?.invoke(info.hasPremium()) },
            )
        }
    }

    override suspend fun refreshPremium(): Boolean? {
        if (!ready()) return null
        return suspendCancellableCoroutine { continuation ->
            runCatching {
                Purchases.sharedInstance.getCustomerInfoWith(
                    onError = { if (continuation.isActive) continuation.resume(null) },
                    onSuccess = { info ->
                        if (continuation.isActive) continuation.resume(info.hasPremium())
                    },
                )
            }.onFailure { if (continuation.isActive) continuation.resume(null) }
        }
    }

    override suspend fun loadProducts(): List<PaywallProduct> {
        if (!ready()) return emptyList()
        val offering = suspendCancellableCoroutine { continuation ->
            runCatching {
                Purchases.sharedInstance.getOfferingsWith(
                    onError = { if (continuation.isActive) continuation.resume(null) },
                    onSuccess = { offerings ->
                        if (continuation.isActive) continuation.resume(offerings.current)
                    },
                )
            }.onFailure { if (continuation.isActive) continuation.resume(null) }
        } ?: return emptyList()
        val byKind = linkedMapOf<PaywallProductKind, PaywallProduct>()
        for (pkg in offering.availablePackages) {
            val product = runCatching { pkg.toPaywallProduct() }.getOrNull() ?: continue
            byKind.putIfAbsent(product.kind, product)
        }
        return PaywallProductKind.entries.mapNotNull { byKind[it] }
    }

    override suspend fun purchase(activity: Activity, product: PaywallProduct): PurchaseResult {
        val pkg = product.rcPackage ?: return PurchaseResult.Unavailable
        if (!ready()) return PurchaseResult.Unavailable
        return suspendCancellableCoroutine { continuation ->
            runCatching {
                Purchases.sharedInstance.purchaseWith(
                    PurchaseParams.Builder(activity, pkg).build(),
                    onError = { error, userCancelled ->
                        if (!continuation.isActive) return@purchaseWith
                        continuation.resume(
                            when {
                                userCancelled -> PurchaseResult.Cancelled
                                else -> PurchaseResult.Failed(
                                    error.message.ifBlank { "The purchase could not be completed." },
                                )
                            },
                        )
                    },
                    onSuccess = { _, info ->
                        if (!continuation.isActive) return@purchaseWith
                        statusListener?.invoke(info.hasPremium())
                        continuation.resume(
                            if (info.hasPremium()) {
                                PurchaseResult.Purchased
                            } else {
                                PurchaseResult.Failed(
                                    "The purchase finished, but Premium is not active yet. " +
                                        "Try Restore purchases in a moment.",
                                )
                            },
                        )
                    },
                )
            }.onFailure {
                if (continuation.isActive) continuation.resume(PurchaseResult.Unavailable)
            }
        }
    }

    override suspend fun restorePurchases(): PurchaseResult {
        if (!ready()) return PurchaseResult.Unavailable
        return suspendCancellableCoroutine { continuation ->
            runCatching {
                Purchases.sharedInstance.restorePurchasesWith(
                    onError = { error ->
                        if (continuation.isActive) {
                            continuation.resume(
                                PurchaseResult.Failed(
                                    error.message.ifBlank { "Restore could not be completed." },
                                ),
                            )
                        }
                    },
                    onSuccess = { info ->
                        if (!continuation.isActive) return@restorePurchasesWith
                        statusListener?.invoke(info.hasPremium())
                        continuation.resume(
                            if (info.hasPremium()) {
                                PurchaseResult.Purchased
                            } else {
                                PurchaseResult.Failed(
                                    "No earlier purchase was found for this store account.",
                                )
                            },
                        )
                    },
                )
            }.onFailure {
                if (continuation.isActive) continuation.resume(PurchaseResult.Unavailable)
            }
        }
    }

    /** True when this build has a key and the SDK finished configuring. */
    private fun ready(): Boolean =
        isConfigured && runCatching { Purchases.isConfigured }.getOrDefault(false)

    private fun CustomerInfo.hasPremium(): Boolean =
        entitlements[ENTITLEMENT_PREMIUM]?.isActive == true

    private fun Package.toPaywallProduct(): PaywallProduct? {
        val kind = kindOf(product, packageType) ?: return null
        val periodLabel = when (kind) {
            PaywallProductKind.MONTHLY -> "per month"
            PaywallProductKind.ANNUAL -> "per year"
            PaywallProductKind.LIFETIME -> "one-time payment"
        }
        val title = product.title.ifBlank {
            when (kind) {
                PaywallProductKind.MONTHLY -> "Premium monthly"
                PaywallProductKind.ANNUAL -> "Premium annual"
                PaywallProductKind.LIFETIME -> "Premium lifetime"
            }
        }
        // The trial line comes only from the store's own data for the
        // annual product: a free phase the store published. No free
        // phase in the data, no trial promised on the paywall.
        val trialLabel = if (kind == PaywallProductKind.ANNUAL) {
            val freePhase = product.subscriptionOptions
                ?.firstOrNull { it.freePhase != null }?.freePhase
            freePhase?.let { TrialText.trialLabel(it.billingPeriod.iso8601, product.price.formatted) }
        } else {
            null
        }
        return PaywallProduct(
            kind = kind,
            productId = product.id,
            title = title,
            priceFormatted = product.price.formatted,
            periodLabel = periodLabel,
            trialLabel = trialLabel,
            rcPackage = this,
        )
    }

    private fun kindOf(product: StoreProduct, packageType: PackageType): PaywallProductKind? =
        when (product.id) {
            PRODUCT_MONTHLY -> PaywallProductKind.MONTHLY
            PRODUCT_ANNUAL -> PaywallProductKind.ANNUAL
            PRODUCT_LIFETIME -> PaywallProductKind.LIFETIME
            else -> when (packageType) {
                PackageType.MONTHLY -> PaywallProductKind.MONTHLY
                PackageType.ANNUAL -> PaywallProductKind.ANNUAL
                PackageType.LIFETIME -> PaywallProductKind.LIFETIME
                else -> null
            }
        }
}
