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
