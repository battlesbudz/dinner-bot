package com.battlesbudz.dinnerbot.core.model

import java.time.LocalDate

/**
 * Savings history (Phase C3): what past planned weeks saved against
 * their budgets, summed up for the savings history / reports screen,
 * which is itself a premium feature (see [PremiumFeature.SAVINGS_HISTORY]).
 *
 * The history is computed from one record per planned week — the week's
 * budget and the cash the week's shopping actually needed (see
 * [SavingsWeek]). There is no other input: no receipts, no per-item
 * detail. This file holds only the pure arithmetic so it can be tested
 * on the JVM; gathering the records belongs to the data layer.
 *
 * The rules in plain language:
 *
 * - A week "saved" the part of its budget it did not need:
 *   budget minus cash needed, and never less than zero. A week that
 *   needed more than its budget saved nothing — it does not drag the
 *   totals negative.
 * - A week counts as under budget when the cash it needed was no more
 *   than its budget. Needing exactly the budget counts: the week lived
 *   within its means.
 * - A record whose week start cannot be read as a date is skipped
 *   everywhere. One damaged record never breaks the whole report and
 *   never counts toward any number in it.
 */

/**
 * One planned week's savings record.
 *
 * [weekStart] is the week's start as an ISO date string ("yyyy-MM-dd"),
 * the same shape plans store. It is kept as a string here because that
 * is how it is stored; [SavingsHistory.report] parses it and skips the
 * record if it does not parse. Money is integer cents, as everywhere in
 * Dinner Bot.
 */
data class SavingsWeek(
    /** ISO date string ("yyyy-MM-dd") of the first day of the planned week. */
    val weekStart: String,
    /** The week's budget, in cents. */
    val budgetCents: Int,
    /** The cash the week's shopping actually needed, in cents. */
    val cashNeededCents: Int,
) {
    /** What this week saved: budget minus cash needed, floored at zero. */
    val savedCents: Int
        get() = maxOf(0, budgetCents - cashNeededCents)

    /** True when this week needed no more cash than its budget (exactly the budget counts). */
    val underBudget: Boolean
        get() = cashNeededCents <= budgetCents
}

/**
 * The savings report over a set of planned weeks.
 *
 * All counts and sums cover only the records whose week start parses
 * (see [SavingsHistory]); a skipped record contributes to nothing here,
 * not even [weeksPlanned].
 */
data class SavingsReport(
    /** Savings from weeks starting in the current calendar month, in cents. */
    val savedThisMonthCents: Int,
    /** Savings from every week in the history, in cents. */
    val totalSavedCents: Int,
    /** Consecutive most-recent under-budget weeks, 7 days apart each (see [SavingsHistory.report]). */
    val currentUnderBudgetStreakWeeks: Int,
    /** How many weeks the history covers. */
    val weeksPlanned: Int,
    /** How many of those weeks came in under budget. */
    val weeksUnderBudget: Int,
)

/** Pure savings arithmetic over [SavingsWeek] records. */
object SavingsHistory {

    /** A week paired with its parsed start date, so parsing happens exactly once. */
    private data class DatedWeek(val start: LocalDate, val week: SavingsWeek)

    /** The parsed record for [week], or null when its week start does not parse as an ISO date. */
    private fun datedOrNull(week: SavingsWeek): DatedWeek? {
        val start = runCatching { LocalDate.parse(week.weekStart.trim()) }.getOrNull() ?: return null
        return DatedWeek(start, week)
    }

    /**
     * The report over [weeks], as of [today].
     *
     * - Records with an unparseable week start are skipped everywhere.
     * - [SavingsReport.savedThisMonthCents] sums [SavingsWeek.savedCents]
     *   over the weeks whose start falls in [today]'s calendar month —
     *   same year and same month; a week from the same month last year
     *   is not this month.
     * - [SavingsReport.totalSavedCents] sums every (parseable) week, and
     *   [SavingsReport.weeksUnderBudget] counts the under-budget ones.
     *
     * The streak counts back from the most recent week. Weeks are first
     * made distinct by start date — if the same week start appears more
     * than once, the LAST record in [weeks] wins, because the newest
     * record for a week is the corrected one — then ordered newest
     * first. Starting from the newest week, each week counts while it is
     * under budget and starts exactly 7 days before the week counted
     * just before it. The first week that is over budget, or that is not
     * exactly 7 days earlier than its predecessor, ends the streak: a
     * missing week breaks it just as an over-budget week does. If the
     * most recent week itself is over budget, the streak is 0. Note the
     * distinct-by-date step is part of the streak only; the sums and
     * counts above read every parseable record as given.
     */
    fun report(weeks: List<SavingsWeek>, today: LocalDate): SavingsReport {
        val dated = weeks.mapNotNull { datedOrNull(it) }

        val savedThisMonth = dated
            .filter { it.start.year == today.year && it.start.month == today.month }
            .sumOf { it.week.savedCents }
        val totalSaved = dated.sumOf { it.week.savedCents }
        val weeksUnderBudget = dated.count { it.week.underBudget }

        // Streak: distinct by start date, last record in the input wins,
        // then newest first.
        val distinctByStart = LinkedHashMap<LocalDate, DatedWeek>()
        for (entry in dated) distinctByStart[entry.start] = entry
        val newestFirst = distinctByStart.values.sortedByDescending { it.start }

        var streak = 0
        var previousStart: LocalDate? = null
        for (entry in newestFirst) {
            if (!entry.week.underBudget) break
            val previous = previousStart
            if (previous != null && entry.start != previous.minusDays(7)) break
            streak++
            previousStart = entry.start
        }

        return SavingsReport(
            savedThisMonthCents = savedThisMonth,
            totalSavedCents = totalSaved,
            currentUnderBudgetStreakWeeks = streak,
            weeksPlanned = dated.size,
            weeksUnderBudget = weeksUnderBudget,
        )
    }
}
