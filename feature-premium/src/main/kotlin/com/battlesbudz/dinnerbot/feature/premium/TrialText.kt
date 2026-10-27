package com.battlesbudz.dinnerbot.feature.premium

/**
 * Words for a free-trial length, from the ISO-8601 billing period the
 * store reports for a subscription's free phase ("P14D", "P2W",
 * "P1M", "P1Y"). The paywall only ever shows a trial length the store
 * itself published; when the period cannot be read, the label falls
 * back to naming the trial without a length rather than inventing one.
 */
internal object TrialText {

    /** "14 days" / "2 weeks" / "1 month" for a simple period, else null. */
    fun periodLabel(iso8601: String?): String? {
        if (iso8601.isNullOrBlank()) return null
        val match = Regex("""^P(\d+)([DWMY])$""").matchEntire(iso8601.trim().uppercase())
            ?: return null
        val count = match.groupValues[1].toIntOrNull() ?: return null
        if (count <= 0) return null
        val unit = when (match.groupValues[2]) {
            "D" -> if (count == 1) "day" else "days"
            "W" -> if (count == 1) "week" else "weeks"
            "M" -> if (count == 1) "month" else "months"
            else -> if (count == 1) "year" else "years"
        }
        return "$count $unit"
    }

    /**
     * The annual plan's trial line for the paywall: "14-day free trial,
     * then $39.99/year" when the store's free-phase period reads
     * cleanly (a two-week phase also reads as 14 days, matching how
     * Play words it), or null when there is no readable free phase —
     * in which case the paywall shows no trial line at all.
     */
    fun trialLabel(freePhaseIso8601: String?, priceFormatted: String): String? {
        val period = periodLabel(freePhaseIso8601) ?: return null
        val length = when (period) {
            "2 weeks" -> "14-day"
            "1 week" -> "7-day"
            // "14 days" -> "14-day", "1 month" -> "1-month", "3 months" -> "3-month".
            else -> period.removeSuffix("s").replace(' ', '-')
        }
        return "$length free trial, then $priceFormatted/year"
    }
}
