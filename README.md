# Dinner Bot

Dinner Bot is a local-first budget dinner planner for one household. It plans a week of dinners against a weekly cash budget, builds one grocery list split across the three stores the household actually uses (Aldi for ingredients, Walmart for frozen foods, Sam's Club for bulk meats and packaged goods), and keeps working fully offline, at home and at the store.

## Design

- **Local-first, no server.** Everything lives on the phone in a local database. No account, no sign-in, no subscription, no ads, no tracking. The app does not even request the network permission.
- **Clean room.** Dinner Bot does the job of subscription budget meal planners, with new code, new words, and its own recipes and price data.
- **Seed data is data, not code.** Recipes and the price book live in `seed-data/` as JSON and are the single source of truth: the app bundles them as assets and the `data-seed` module validates them in tests (every recipe ingredient maps to a priced item, no zero prices, pack sizes present).
- **Money is integer cents** everywhere. The weekly budget caps **cash spent** each week across all three stores combined; Sam's Club bulk packs charge their full price in the week they are bought and $0 in weeks that eat from home stock, and portion cost is tracked as information.

## Household rules baked in

- **No beef, ever.** This household always substitutes ground turkey for ground beef, in planning, costing, and lists.
- **Three-store routine.** Every price book item has a home store; a week's list can cover all three stores.
- **Sam's Club has to earn the trip.** Bulk buying is only planned when projected Sam's savings for the month reach at least $15 against regular packs elsewhere.
- Prices are estimates. The target is that a quoted week lands within $10 of what is actually spent on those items; per-store calibration and in-app price corrections (later phases) are how that stays true.

## Status: Phase 1

Phase 1 is the skeleton: project structure in isolated packages with per-package unit tests, the Room database, the seed loader and validator, onboarding/settings, and recipe browsing with serving scaling and estimated cost per serving.

Later phases (not built yet): the budget planner/solver and grocery list (Phase 2), shopping mode and price learning (Phase 3), backup export/import and the home trial (Phase 4), and the four-week accuracy trial (Phase 5). The modules `feature-pricebook`, `feature-pantry`, `feature-planner`, `feature-grocerylist`, and `feature-backup` exist as stubs.

CI runs unit tests only (`./gradlew test`). No APK is assembled in CI.

## License

Undecided. A license will be chosen before any public release.
