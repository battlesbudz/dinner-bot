package com.battlesbudz.dinnerbot.feature.premium

/**
 * The app's legal and contact links (Phase C4).
 *
 * [PRIVACY_POLICY_URL] is the live GitHub Pages copy of the policy in
 * this repo's /docs folder, published from the `commercial` branch. The
 * paywall and the Settings Premium section link to it; the same URL goes
 * into the Play listing.
 *
 * [SUPPORT_EMAIL] is a PLACEHOLDER. The dedicated Dinner Bot support
 * Gmail does not exist yet (a Battles-needed launch item, issue #41);
 * when it is created, replace this one constant and the matching
 * address in docs/privacy-policy.html, then ship an update before the
 * Play submission — see the launch runbook on issue #41, step (e).
 */
object LegalLinks {

    const val PRIVACY_POLICY_URL =
        "https://battlesbudz.github.io/dinner-bot/privacy-policy.html"

    /** PLACEHOLDER — see the class doc. Not a real inbox yet. */
    const val SUPPORT_EMAIL = "dinnerbot.support@gmail.com"
}
