# Dinner Bot

Dinner Bot is a budget-first weekly meal planner for Android. It plans a week of dinners against a weekly budget, builds one grocery list from the plan, and helps you keep track of what you already have at home, what things cost, and what you actually spent.

This branch (`commercial`) is the commercial version of Dinner Bot, in development for a Google Play release in the United States. It is a separate, long-lived branch and is not the free household app on `main`.

## What the app does today

- **Planner:** Generates a week of dinners for your cook nights and prices the week against your weekly budget, showing cash needed for the week and the estimated food cost as separate numbers.
- **Recipes:** Browse a starter recipe library, scale servings, see the estimated cost per serving, rate recipes, add your own recipes, and edit recipe ingredients and steps.
- **Recipe import:** Import recipes by sharing them to the app, pasting a link, or pasting recipe text, then review and edit the imported ingredients before saving.
- **Grocery list:** Builds one list from the week's plan, scaled to servings, subtracts what your pantry already covers, groups items by store, and keeps check-offs as you shop. Tapping a price corrects it, and the correction is remembered in the price book.
- **Price book:** Your prices for each item, by store and pack size, all editable. Prices you correct or scan replace starter prices.
- **Pantry:** Track staple items you have at home and quantity-tracked bulk stock, so the planner and the list do not tell you to buy what you already own.
- **Barcode scanning:** Scan a product barcode while adding an ingredient, shopping, or correcting a price. The scan can suggest a product name and label nutrition from the public Open Food Facts database, and the price you enter is remembered for that item. The camera permission is requested only when you start a scan.
- **Nutrition:** See nutrition totals for a planned week and per-serving figures on each recipe. Figures come from product label data captured at scan time where available, with USDA FoodData Central values as the fallback, and totals that are missing values are marked as partial.
- **Reminders:** Local reminders, including defrost reminders, set by you.
- **Backup:** Export and import your settings, price book, pantry, ratings, and learned barcode links as one file through the Android system file picker. Sharing is by file export and import.
- **Week rollover and reconciliation:** At the end of a week, check what was actually bought and used, carry the pantry forward, and reconcile the plan against what happened.

The app is local-first: your recipes, prices, pantry, and plans live in a local database on your phone, and the core planning, list, and recipe features work offline. An internet connection is used only for optional assists you start, such as a barcode product lookup, and for importing a recipe from a link you paste.

## Project structure

Recipes and starter price data live in `seed-data/` as JSON and are the single source of truth for starter data: the app bundles them as assets and the `data-seed` module validates them in tests. Application code is organized into isolated feature packages (planner, recipes, grocery list, price book, pantry, barcode, nutrition, import, backup, reminders, onboarding), with unit tests per package. Money is handled in integer cents throughout.

CI runs per-package JVM unit tests, Android lint, and instrumented tests on an API 34 emulator.

## Status

The commercial version is in active development and has not been released yet. Generalizing starter data, stores, and defaults for public users, expanding the starter recipe library, and adding the planned free and premium tiers are tracked in the Dinner Bot Commercial milestone and its phase issues in this repository. Until those phases land, some starter data and defaults still reflect early development.

Distribution for this version will be through Google Play only. No APK for this branch is published as a GitHub Release.

## License

Copyright (c) 2026 Justin Battles. All rights reserved. See [LICENSE](LICENSE).

The source in this repository is visible for reading only. No copying, reuse, redistribution, or derivative rights are granted.
