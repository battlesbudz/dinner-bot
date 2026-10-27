package com.battlesbudz.dinnerbot.core.model

import java.time.LocalDate

/**
 * The freemium layer (Phase C3, locked decisions 2026-10-26).
 *
 * Dinner Bot is free to use. Premium unlocks a short, complete list of
 * extra features and lifts two monthly caps; everything else in the app
 * is never gated. This file holds only the pure rules — what is gated,
 * how much a free user has left, and when the app may remind a free user
 * that premium exists — so the rules can be tested on the JVM without a
 * phone, a store, or a network. Storing the state, fetching the real
 * entitlement, and drawing any prompt belong to other layers.
 *
 * The rules in plain language:
 *
 * - Recipe imports: a free user may import 2 recipes per calendar month.
 *   Premium imports without a cap.
 * - Barcode scans: a new free user first gets 15 scans to spend whenever
 *   they like. That is a one-time starter allowance, not a monthly one —
 *   it never refills and it never expires at a month boundary. Only once
 *   every starter scan is gone does a monthly pool open: 10 free scans
 *   per calendar month. The two pools are sequential, never added
 *   together, so a free user never has 25 scans available in a month.
 *   Premium scans without a cap.
 * - Entitlement fails soft: the app caches the last known premium status
 *   on the device, and when a fresh status cannot be fetched the cached
 *   value stands (see [PremiumRules.resolvePremium]). A failed fetch
 *   never takes premium away from someone who had it, and never grants
 *   it to someone who did not.
 * - The periodic in-app premium prompt is deliberately quiet: never
 *   during onboarding, never before the user has planned at least one
 *   week, never in the first 3 days after the premium state is first
 *   seen on the device, and dismissing it suppresses it for 30 days.
 *   Premium users never see it at all. (Prompts tied to hitting a cap —
 *   trying to import a third recipe, for example — are a separate matter
 *   handled where the cap is hit; [PremiumRules.shouldShowPeriodicPrompt]
 *   governs only the periodic reminder.)
 */

/**
 * One premium-gated feature. This is the complete list: a capability that
 * is not named here is never gated, for anyone, ever.
 */
enum class PremiumFeature {
    /** Importing a recipe once the free monthly import cap is used up. */
    RECIPE_IMPORT,

    /** Scanning a barcode once the free scan allowances are used up. */
    BARCODE_SCAN,

    /** Planning two weeks at a time instead of one. */
    TWO_WEEK_PLANNING,

    /** Using the bundled diet presets to filter planning. */
    DIET_PRESETS,

    /** Seeing savings history and savings reports over past weeks. */
    SAVINGS_HISTORY,
}

/**
 * How much of the free allowances a user has spent.
 *
 * The monthly counters name the month they belong to as a "yyyy-MM" key
 * (see [PremiumRules.monthKey]); a counter whose month is not the current
 * month belongs to a past month and reads as zero spent this month (see
 * [PremiumRules.rolledCounters]). A null month means the pool has never
 * been touched. [starterScansUsed] is different on purpose: the starter
 * allowance is a lifetime pool, so it carries no month and never resets.
 *
 * Counts are only ever increased by [PremiumRules.afterScan] and
 * [PremiumRules.afterImport], each of which rolls the months forward
 * first, so a stored value is always safe to pass back in as-is.
 */
data class UsageCounters(
    /** Starter barcode scans spent so far, out of [PremiumRules.STARTER_SCAN_ALLOWANCE]. Never resets. */
    val starterScansUsed: Int = 0,
    /** The "yyyy-MM" month [scansUsedInMonth] belongs to, or null if no monthly scan has been spent. */
    val scanMonth: String? = null,
    /** Barcode scans spent in [scanMonth] from the monthly pool. */
    val scansUsedInMonth: Int = 0,
    /** The "yyyy-MM" month [importsUsedInMonth] belongs to, or null if no import has been spent. */
    val importMonth: String? = null,
    /** Recipe imports spent in [importMonth]. */
    val importsUsedInMonth: Int = 0,
)

/**
 * Everything about premium the device keeps for itself, in one place.
 *
 * [cachedPremium] is the fail-soft cache: the last premium status the
 * app actually learned. [premiumCheckedAtMillis] records when that value
 * was learned, for display and debugging; no rule in this file expires
 * the cache, because the locked fail-soft rule is that the cached value
 * stands until a fresh one replaces it.
 *
 * [firstSeenAtMillis] is when this state was first created on the
 * device; 0 means it has not been stamped yet, which the prompt rule
 * treats as "too early to prompt". [lastPromptDismissedAtMillis] is when
 * the user last dismissed the periodic premium prompt; 0 means never.
 */
data class PremiumLocalState(
    /** The last known premium status; stands whenever a fresh status cannot be fetched. */
    val cachedPremium: Boolean = false,
    /** When [cachedPremium] was last learned, in epoch milliseconds; 0 if never checked. */
    val premiumCheckedAtMillis: Long = 0L,
    /** The free-allowance spending counters. */
    val counters: UsageCounters = UsageCounters(),
    /** When the periodic premium prompt was last dismissed, in epoch milliseconds; 0 if never. */
    val lastPromptDismissedAtMillis: Long = 0L,
    /** When this state was first seen on the device, in epoch milliseconds; 0 if not yet stamped. */
    val firstSeenAtMillis: Long = 0L,
)

/** The pure freemium rules. Every function here is side-effect free. */
object PremiumRules {

    /** Barcode scans a new free user gets to spend whenever they like, before the monthly pool opens. */
    const val STARTER_SCAN_ALLOWANCE: Int = 15

    /** Free barcode scans per calendar month, available only once the starter allowance is gone. */
    const val MONTHLY_SCAN_ALLOWANCE: Int = 10

    /** Free recipe imports per calendar month. */
    const val MONTHLY_IMPORT_ALLOWANCE: Int = 2

    /** Quiet period after the state is first seen before any periodic prompt may show: 3 days. */
    const val PROMPT_SETTLE_IN_MILLIS: Long = 3L * 24 * 60 * 60 * 1000

    /** How long dismissing the periodic prompt suppresses it: 30 days. */
    const val PROMPT_DISMISSAL_MILLIS: Long = 30L * 24 * 60 * 60 * 1000

    /**
     * The "yyyy-MM" key naming [date]'s calendar month, zero-padded so
     * keys sort in calendar order, e.g. "2026-10" and "2026-01".
     */
    fun monthKey(date: LocalDate): String =
        "${date.year.toString().padStart(4, '0')}-${date.monthValue.toString().padStart(2, '0')}"

    /**
     * [counters] as they read on [today]: each monthly pool that belongs
     * to a month other than today's — including a pool never touched,
     * whose month is still null — starts today's month with 0 spent, and
     * its month stamp becomes today's key. The scan and import months
     * roll independently, because a user can easily scan in a month they
     * import nothing. The starter count never resets: it has no month.
     *
     * Rolling is just a reading rule — this returns a new value and
     * changes nothing stored — so every other rule here rolls first and
     * callers never have to remember to.
     */
    fun rolledCounters(counters: UsageCounters, today: LocalDate): UsageCounters {
        val key = monthKey(today)
        val scansRolled = if (counters.scanMonth == key) {
            counters
        } else {
            counters.copy(scanMonth = key, scansUsedInMonth = 0)
        }
        return if (scansRolled.importMonth == key) {
            scansRolled
        } else {
            scansRolled.copy(importMonth = key, importsUsedInMonth = 0)
        }
    }

    /**
     * Free barcode scans left on [today]. While any starter scan remains,
     * this is the starter remainder ONLY: the monthly pool has not opened
     * yet, so its 10 are not added on top. Once the starter pool is empty
     * this is the monthly remainder. Never negative, however far past an
     * allowance a stored count somehow got.
     */
    fun scansRemaining(counters: UsageCounters, today: LocalDate): Int {
        val rolled = rolledCounters(counters, today)
        val starterRemaining = (STARTER_SCAN_ALLOWANCE - rolled.starterScansUsed).coerceAtLeast(0)
        if (starterRemaining > 0) return starterRemaining
        return (MONTHLY_SCAN_ALLOWANCE - rolled.scansUsedInMonth).coerceAtLeast(0)
    }

    /** True when one more barcode scan is allowed: always for premium, otherwise while scans remain. */
    fun canScan(isPremium: Boolean, counters: UsageCounters, today: LocalDate): Boolean =
        isPremium || scansRemaining(counters, today) > 0

    /**
     * The counters after one barcode scan is spent: roll the months
     * forward to [today] first, then take the scan from the starter pool
     * while it has any left, and from the monthly pool only after that.
     * Callers check [canScan] before spending; this function simply
     * records the spend in the right pool.
     */
    fun afterScan(counters: UsageCounters, today: LocalDate): UsageCounters {
        val rolled = rolledCounters(counters, today)
        return if (rolled.starterScansUsed < STARTER_SCAN_ALLOWANCE) {
            rolled.copy(starterScansUsed = rolled.starterScansUsed + 1)
        } else {
            rolled.copy(scansUsedInMonth = rolled.scansUsedInMonth + 1)
        }
    }

    /** Free recipe imports left in [today]'s calendar month, after rolling. Never negative. */
    fun importsRemaining(counters: UsageCounters, today: LocalDate): Int {
        val rolled = rolledCounters(counters, today)
        return (MONTHLY_IMPORT_ALLOWANCE - rolled.importsUsedInMonth).coerceAtLeast(0)
    }

    /** True when one more recipe import is allowed: always for premium, otherwise while imports remain. */
    fun canImport(isPremium: Boolean, counters: UsageCounters, today: LocalDate): Boolean =
        isPremium || importsRemaining(counters, today) > 0

    /** The counters after one recipe import is spent: roll to [today] first, then add one import. */
    fun afterImport(counters: UsageCounters, today: LocalDate): UsageCounters {
        val rolled = rolledCounters(counters, today)
        return rolled.copy(importsUsedInMonth = rolled.importsUsedInMonth + 1)
    }

    /**
     * The gate matrix for features without a counter of their own: every
     * feature in [PremiumFeature] requires premium, so this is simply
     * [isPremium], whichever feature is asked about. It exists as a
     * function so no screen invents its own list of what is gated — the
     * enum is the list, and this is the rule. (Imports and scans also
     * appear in the enum for completeness; below their free caps they are
     * allowed without premium, which is what [canImport] and [canScan]
     * decide.)
     */
    fun isAllowed(feature: PremiumFeature, isPremium: Boolean): Boolean = isPremium

    /**
     * The fail-soft entitlement rule: a freshly fetched status, when
     * there is one, is the answer; when the fetch produced nothing
     * ([fetchedPremium] is null — offline, store unreachable, any
     * failure), the cached on-device value stands, whichever way it
     * points.
     */
    fun resolvePremium(cachedPremium: Boolean, fetchedPremium: Boolean?): Boolean =
        fetchedPremium ?: cachedPremium

    /**
     * True when the periodic in-app premium prompt may show at
     * [nowMillis]. Every one of these must hold; any single failure
     * suppresses the prompt:
     *
     * - the user is not premium ([PremiumLocalState.cachedPremium] —
     *   the cached value, per the fail-soft rule);
     * - onboarding is complete — never during onboarding;
     * - the user has planned at least one week ([hasAnyPlan]) — never
     *   before the app has proven useful;
     * - the state was stamped ([PremiumLocalState.firstSeenAtMillis]
     *   is not 0) at least [PROMPT_SETTLE_IN_MILLIS] ago — not during
     *   the first 3 days;
     * - the prompt was never dismissed, or was dismissed at least
     *   [PROMPT_DISMISSAL_MILLIS] ago — a dismissal buys 30 quiet days,
     *   and the prompt becomes eligible again the moment they elapse.
     */
    fun shouldShowPeriodicPrompt(
        state: PremiumLocalState,
        onboardingComplete: Boolean,
        hasAnyPlan: Boolean,
        nowMillis: Long,
    ): Boolean {
        if (state.cachedPremium) return false
        if (!onboardingComplete) return false
        if (!hasAnyPlan) return false
        if (state.firstSeenAtMillis == 0L) return false
        if (nowMillis - state.firstSeenAtMillis < PROMPT_SETTLE_IN_MILLIS) return false
        if (state.lastPromptDismissedAtMillis > 0L &&
            nowMillis - state.lastPromptDismissedAtMillis < PROMPT_DISMISSAL_MILLIS
        ) {
            return false
        }
        return true
    }
}
