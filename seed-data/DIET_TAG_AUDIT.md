# Diet tag audit — Phase V2-2 (2026-10-22)

The 36 seed recipes' diet tags, checked recipe by recipe against each
recipe's actual ingredient list, so the diet presets (diet-presets.json)
filter on true tags. Tags describe the recipe **as written**: an ingredient
counts toward the verdict whether it is marked essential or removable,
because the tag is about the dish on the page, not about one household's
adapted version of it.

## Vocabulary

- **Vegetarian**: no meat ingredient (ground turkey, chicken breast, pork
  tenderloin). Egg, cheese, and bean dinners qualify, matching how the tags
  were already used.
- **Gluten-free**: no wheat carrier anywhere in the ingredient list:
  spaghetti, rotini, egg noodles, flour tortillas, white bread, hamburger
  buns, Texas toast, pantry flour, and soy sauce (the common form of soy
  sauce is brewed with wheat, so any dish listing it is not tagged).

## Verdicts

**Vegetarian — verified complete, no changes.** Exactly the 10 egg and
cheese dinners carry the tag (egg-fried-rice, tomato-soup-grilled-cheese,
breakfast-burritos, veggie-frittata, cheese-omelets-home-fries,
rotini-mac-and-cheese, cheese-quesadillas, veggie-pasta-bake,
cheesy-potato-skillet, bean-cheese-burritos), and no meat dinner carries it.

**Gluten-free — 3 tags added, none removed.** Already tagged and verified
against their ingredient lists: turkey-chili, turkey-bean-chili,
veggie-frittata, cheese-omelets-home-fries, cheesy-potato-skillet.
Added in this phase (no wheat carrier in the list):

- sheet-pan-pork-potatoes (pork, potatoes, green beans, onion, spices)
- turkey-shepherds-pie (turkey, potatoes, milk, peas and carrots, onion, cheddar, butter)
- potato-egg-skillet (eggs, potatoes, peppers, onion, cheddar)

**Judgment calls worth naming:**

- cubed-pork-potato-skillet is NOT tagged gluten-free: its sauce uses
  pantry flour. The flour is marked removable, so a gluten-free household
  gets the dish adapted, not blocked — the won't-eat machinery in the
  gluten-free preset handles it dish by dish.
- Dishes with soy sauce (pork-green-bean-skillet, chicken-rice-skillet,
  egg-fried-rice, pork-fried-rice, the stir fries) are NOT tagged
  gluten-free even where the soy sauce is removable, for the reason in the
  vocabulary note above.
- turkey-chili and turkey-bean-chili keep their tags: their ingredient
  lists contain no wheat carrier (the canned tomato soup in our data lists
  no flour). As the app already says of the won't-eat list, tags and
  presets are planning filters, not medical or allergen guarantees.

---

# Addendum — Phase C2 starter library expansion (2026-10-27)

The library grew from 36 to 100 recipes. The V2-2 vocabulary above is
unchanged and was applied to the 64 new recipes ingredient by ingredient;
the tags below are pinned in SeedDataFilesTest.dietTagsMatchTheAudit.

## Vocabulary extensions

- **Vegetarian**: the meat set the tag excludes now also names the C2
  proteins — ground beef, beef chuck, Italian sausage, bacon, breakfast
  sausage, salmon, cod, shrimp, canned tuna, pepperoni, and the two
  broths (chicken and beef broth are meat-derived, so a dish listing
  either is never tagged vegetarian; the vegetarian soups in the
  library are written without broth).
- **Gluten-free**: the wheat-carrier list grows by the C2 pasta and
  crumb items — penne, lasagna noodles, and breadcrumbs join spaghetti,
  rotini, egg noodles, flour tortillas, white bread, hamburger buns,
  Texas toast, pantry flour, and soy sauce as automatic exclusions.

## Verdicts for the new recipes

**Vegetarian — 16 of the new recipes.** veggie-lasagna,
lentil-vegetable-soup, chickpea-tacos, black-bean-burgers,
tofu-stir-fry, lasagna-roll-ups, mushroom-stroganoff,
sweet-potato-chili, veggie-lo-mein, potato-corn-chowder,
broccoli-cheddar-soup, rice-bean-bowls, broccoli-cheddar-quiche,
shakshuka, breakfast-hash-eggs, cheese-enchiladas. The library total
is 26.

**Gluten-free — 12 of the new recipes.** beef-pot-roast, beef-chili,
beef-cabbage-skillet, oven-bbq-chicken, stuffed-bell-peppers,
turkey-sweet-potato-skillet, garlic-butter-salmon, cajun-shrimp-rice,
lentil-vegetable-soup, sweet-potato-chili, rice-bean-bowls,
breakfast-hash-eggs. The library total is 20.

**Judgment calls worth naming:**

- Processed meats are never tagged gluten-free in this library, even
  where a dish's other ingredients would qualify: retail Italian
  sausage, breakfast sausage, and bacon vary in fillers and casings,
  so sausage-and-peppers-penne, sausage-lentil-soup, breakfast-skillet,
  sausage-gravy-biscuits, sausage-potato-soup, and blt-sandwiches
  carry no gluten-free tag. (Most also list a wheat carrier anyway;
  breakfast-skillet and sausage-lentil-soup are the two the rule
  actually decides.)
- tomato-mozzarella-flatbreads is NOT tagged vegetarian: its
  pepperoni line is removable, but tags describe the recipe as
  written, per the rule at the top of this audit.
- oven-bbq-chicken keeps a gluten-free tag on the same basis as the
  V2-2 chilis: the barbecue sauce in our data is a generic sauce with
  no wheat carrier listed. Tags remain planning filters, not allergen
  guarantees.
