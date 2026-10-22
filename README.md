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

Phase 1 was the skeleton: project structure in isolated packages with per-package unit tests, the Room database, the seed loader and validator, onboarding/settings, and recipe browsing with serving scaling and estimated cost per serving.

## Status: v0.1.0 (Phase 2)

Phase 2 is the working app:

- **Planner** (`feature-planner`): generates a week of dinners for the household's cook nights, prices it with the shared costing engine in `core-model`, prefers dinners covered by Sam's bulk stock at home, and keeps the week's cash under the budget cap. A Sam's restock that cannot fit is deferred and named in plain language in the plan, never quietly charged over the cap. The week view shows cash needed and attributed food cost as two separate numbers, plus the "Sam's Club saves about $X this month" line from the $15 gate.
- **Grocery list** (`feature-grocerylist`): aggregates the plan's ingredients scaled to servings, subtracts pantry-owned staples and bulk-stock-covered amounts, splits into Aldi / Walmart / Sam's Club sections with per-store cash subtotals, and persists check-offs in Room. Tapping a price corrects it, and the correction writes back to the price book.
- **Price book** (`feature-pricebook`): every item with its store and pack price, editable; corrected prices are flagged and always win over seed data.
- **Pantry** (`feature-pantry`): the owned/not-owned staple checklist plus quantity-tracked Sam's bulk stock.
- **Backup** (`feature-backup`): settings, price book (with corrections), pantry, and bulk stock export/import as one JSON file through the Android system file picker. No network is involved; the app still has no INTERNET permission.
- Plans and grocery lists persist in Room (database version 2; pre-1.0, schema bumps use destructive migration and the seed loader refills).

CI runs per-package JVM unit tests, Android lint, and instrumented tests on an API 34 emulator. Releases are built only from version tags by `release.yml`, signed from repository secrets, and published as GitHub Releases.

## License

Undecided. A license will be chosen before any public release.
