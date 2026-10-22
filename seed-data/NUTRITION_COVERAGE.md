# Phase V2-0 nutrition coverage report

Captured 2026-10-22 from USDA FoodData Central (https://fdc.nal.usda.gov) via the FDC API, full food records. Values are per 100 g of each food exactly as its FDC description states. FoodData Central data is public domain.

- Distinct ingredients in the shipped recipes and price book: **42** (31 price-book items + 11 pantry staples used as recipe ingredients)
- Sourced with a full per-100 g record: **42 of 42**
- Known-incomplete ingredients: **none**. Nothing was estimated; any value FoodData Central does not publish for an entry is stored as null (see field gaps below), never zero-filled or guessed.
- Closest-match entries: **11** — the shipped item is a packaged or branded product (or a mix), and the FDC entry is the closest generic record for that food. Each is flagged `"closestMatch": true` in nutrition.json with the reason in its source note. These are not label values for the package.

## Entries

| Ingredient | FDC ID | FDC entry | Closest match |
|---|---|---|---|
| american-cheese (price book) | 2647439 | Cheese, pasteurized process cheese food or product, American, singles | yes |
| bell-peppers (price book) | 2258588 | Peppers, bell, green, raw | no |
| canned-black-beans (price book) | 2644285 | Beans, black, canned, sodium added, drained and rinsed | no |
| canned-diced-tomatoes (price book) | 333281 | Tomatoes, canned, red, ripe, diced | no |
| canned-kidney-beans (price book) | 2644289 | Beans, kidney, dark red, canned, sodium added, sugar added, drained and rinsed | no |
| canned-tomato-soup (price book) | 172882 | Soup, tomato, canned, condensed | yes |
| chicken-breast (price book) | 2646170 | Chicken, breast, boneless, skinless, raw | no |
| egg-noodles (price book) | 169731 | Noodles, egg, dry, enriched | no |
| eggs (price book) | 171287 | Egg, whole, raw, fresh | no |
| flour-tortillas (price book) | 167535 | Tortillas, ready-to-bake or -fry, flour, shelf stable | yes |
| frozen-broccoli (price book) | 169970 | Broccoli, frozen, spears, unprepared (Includes foods for USDA's Food Distribution Program) | no |
| frozen-green-beans (price book) | 169962 | Beans, snap, green, frozen, all styles, unprepared (Includes foods for USDA's Food Distribution Program) | no |
| frozen-mixed-vegetables (price book) | 170471 | Vegetables, mixed, frozen, unprepared | no |
| frozen-peas-carrots (price book) | 170425 | Peas and carrots, frozen, unprepared | no |
| garden-salad (price book) | 2346388 | Lettuce, iceberg, raw | yes |
| garlic (price book) | 1104647 | Garlic, raw | no |
| garlic-texas-toast (price book) | 167939 | Garlic bread, frozen | yes |
| ground-turkey (price book) | 171505 | Turkey, Ground, raw | no |
| hamburger-buns (price book) | 172796 | Rolls, hamburger or hotdog, plain | yes |
| iceberg-lettuce (price book) | 2346388 | Lettuce, iceberg, raw | no |
| milk (price book) | 746778 | Milk, reduced fat, fluid, 2% milkfat, with added vitamin A and vitamin D | no |
| pasta-sauce (price book) | 332282 | Sauce, pasta, spaghetti/marinara, ready-to-serve | yes |
| pork-tenderloin (price book) | 2646169 | Pork, loin, tenderloin, boneless, raw | no |
| roma-tomatoes (price book) | 170457 | Tomatoes, red, ripe, raw, year round average | no |
| rotini (price book) | 169736 | Pasta, dry, enriched | no |
| russet-potatoes (price book) | 170027 | Potatoes, russet, flesh and skin, raw (Includes foods for USDA's Food Distribution Program) | no |
| shredded-cheddar (price book) | 328637 | Cheese, cheddar | yes |
| sour-cream (price book) | 171257 | Cream, sour, cultured | yes |
| spaghetti (price book) | 169736 | Pasta, dry, enriched | yes |
| white-bread (price book) | 174924 | Bread, white, commercially prepared (includes soft bread crumbs) | yes |
| yellow-onions (price book) | 790646 | Onions, yellow, raw | no |
| black-pepper (pantry staple) | 170931 | Spices, pepper, black | no |
| butter (pantry staple) | 173410 | Butter, salted | no |
| chili-powder (pantry staple) | 171319 | Spices, chili powder | no |
| flour (pantry staple) | 789890 | Flour, wheat, all-purpose, enriched, bleached | no |
| garlic-powder (pantry staple) | 171325 | Spices, garlic powder | no |
| ground-cumin (pantry staple) | 170923 | Spices, cumin seed | no |
| long-grain-white-rice (pantry staple) | 168877 | Rice, white, long-grain, regular, raw, enriched | no |
| paprika (pantry staple) | 171329 | Spices, paprika | no |
| salt (pantry staple) | 173468 | Salt, table | no |
| soy-sauce (pantry staple) | 174277 | Soy sauce made from soy and wheat (shoyu) | no |
| vegetable-oil (pantry staple) | 171411 | Oil, soybean, salad or cooking | no |

## Field gaps (values FDC does not publish for the entry; stored as null)

- caloriesKcal: complete for all 42
- proteinG: complete for all 42
- fatG: complete for all 42
- carbohydrateG: complete for all 42
- fiberG: missing for american-cheese, canned-black-beans, canned-diced-tomatoes, canned-kidney-beans, chicken-breast, garden-salad, iceberg-lettuce, milk, pork-tenderloin, shredded-cheddar, flour
- sugarsG: missing for american-cheese, bell-peppers, canned-black-beans, canned-kidney-beans, canned-tomato-soup, chicken-breast, egg-noodles, eggs, flour-tortillas, frozen-broccoli, frozen-green-beans, frozen-mixed-vegetables, frozen-peas-carrots, garden-salad, garlic, garlic-texas-toast, ground-turkey, hamburger-buns, iceberg-lettuce, pork-tenderloin, roma-tomatoes, rotini, russet-potatoes, sour-cream, spaghetti, white-bread, black-pepper, butter, chili-powder, flour, garlic-powder, ground-cumin, long-grain-white-rice, paprika, salt, soy-sauce, vegetable-oil
- sodiumMg: missing for garlic
- saturatedFatG: missing for bell-peppers, canned-black-beans, canned-diced-tomatoes, canned-kidney-beans, garden-salad, garlic, iceberg-lettuce, yellow-onions, flour
- cholesterolMg: missing for bell-peppers, canned-black-beans, canned-diced-tomatoes, canned-kidney-beans, garden-salad, garlic, iceberg-lettuce, pasta-sauce, yellow-onions, flour

## Spot-check

Three entries were checked digit-for-digit against independently published FoodData Central values on 2026-10-22; all matched:

- Ground turkey, FDC 171505: 148 kcal, protein 19.66 g, fat 7.66 g, saturated fat 2.024 g, sodium 58 mg, cholesterol 69 mg.
- Egg, whole, raw, fresh, FDC 171287: 143 kcal, protein 12.56 g, fat 9.51 g, carbohydrate 0.72 g, cholesterol 372 mg, sodium 142 mg.
- Cheese, cheddar, FDC 328637: 408 kcal, protein 23.3 g, fat 34.0 g, carbohydrate 2.44 g.

## Notes

- The FDC spaghetti-specific Foundation record (fdcId 2758998) currently publishes only a partial nutrient set, so spaghetti uses the complete SR Legacy dry enriched pasta record (fdcId 169736), the same record as rotini; pasta shape does not change the values. Flagged closestMatch in the data.
- Butter, white bread, salt, and vegetable oil likewise use complete SR Legacy records where the newer Foundation records publish partial nutrient sets.
- This data is loaded by the app in Phase V2-6 (nutrition tab). Phase V2-0 is data only: no app code reads nutrition.json yet.
