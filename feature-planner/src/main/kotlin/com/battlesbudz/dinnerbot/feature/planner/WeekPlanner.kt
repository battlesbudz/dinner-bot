package com.battlesbudz.dinnerbot.feature.planner

import com.battlesbudz.dinnerbot.core.model.Costing
import com.battlesbudz.dinnerbot.core.model.GroceryLine
import com.battlesbudz.dinnerbot.core.model.HouseholdSettings
import com.battlesbudz.dinnerbot.core.model.Money
import com.battlesbudz.dinnerbot.core.model.PlannedNight
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeRating
import com.battlesbudz.dinnerbot.core.model.Store

/**
 * The week-planning engine. Pure and deterministic: same inputs and same
 * [variation] always produce the same plan, which is what makes the math
 * unit-testable and the Regenerate button honest.
 *
 * Policy, in order:
 * 1. Plan only from the recipes the caller passes in. The won't-eat rule is
 *    applied before this, by RecipeAdaptation: removable ingredients are
 *    already stripped and recipes needing a won't-eat ingredient are held out.
 *    Thumbs ratings (Phase V2-1) apply here too: a DOWN-rated recipe is out
 *    of generation and swaps entirely, and an UP-rated recipe wins ties
 *    (same stock coverage, same cost) over unrated ones. UP never overrides
 *    cost or budget; unrated recipes stay in normal rotation.
 * 2. Prefer dinners whose Sam's bulk ingredients are already covered by stock
 *    at home (they cost $0 cash for that ingredient), then cheaper dinners.
 * 3. Price the week with [Costing]: cash is what the budget sees.
 * 4. If cash is over the weekly cap, first try swapping dinners for cheaper
 *    ones. A Sam's restock that cannot fit is DEFERRED: dinners depending on
 *    it are swapped away and the restock is named plainly in [WeekPlan.notes]
 *    instead of quietly going over the cap.
 */
object WeekPlanner {

    data class WeekPlan(
        val weekStart: String,
        val nights: List<PlannedNight>,
        val lines: List<GroceryLine>,
        /** Cash needed this week across all stores. The budget number. */
        val cashNeededCents: Int,
        /** Attributed food cost of the dinners. Information only. */
        val foodCostCents: Int,
        /** Projected Sam's Club savings this month, cents. */
        val samsMonthlySavingsCents: Int,
        /** True when the $15/month gate is met and Sam's items are planned at Sam's. */
        val samsGateMet: Boolean,
        /** Item names whose restock was deferred because it could not fit under the cap. */
        val deferredRestocks: List<String>,
        /** Plain-language notes: deferred restocks, pantry gaps, budget warnings. */
        val notes: List<String>,
    )

    fun generate(
        settings: HouseholdSettings,
        recipes: List<Recipe>,
        priceItems: List<PriceItem>,
        stockOnHand: Map<String, Quantity>,
        pantryOwnedNames: Set<String>,
        weekStart: String,
        variation: Int = 0,
        ratings: Map<String, RecipeRating> = emptyMap(),
    ): WeekPlan {
        val itemsById = priceItems.associateBy { it.id }
        val candidates = rankCandidates(settings, recipes, itemsById, stockOnHand, ratings)
        val nightsNeeded = settings.cookNights.size
        if (candidates.isEmpty() || nightsNeeded == 0) {
            val allThumbedDown = recipes.isNotEmpty() &&
                recipes.all { ratings[it.id] == RecipeRating.DOWN }
            return emptyPlan(
                weekStart,
                note = if (allThumbedDown) {
                    "Every dinner is thumbed down right now, so there is nothing to plan. " +
                        "Bring one back from the recipe list by clearing its thumbs down."
                } else {
                    "No dinners are available to plan yet. Check the won't-eat list in Settings."
                },
            )
        }

        // Rotate the ranked list for Regenerate so a new variation is a new week.
        val rotated = rotate(candidates, variation)
        var chosen = rotated.take(nightsNeeded).toMutableList()
        // Thumbs-up tie-break repair: rotation can move an UP-rated dinner
        // out of the taken window in favor of an unrated dinner it exactly
        // ties with (same stock coverage, same cost). Swap it back in so the
        // tie-break holds at every variation, not just variation 0. Anything
        // that differs in stock coverage or cost is left exactly as rotated.
        val chosenIds = chosen.map { it.id }.toMutableSet()
        for (candidate in candidates) {
            if (ratings[candidate.id] != RecipeRating.UP || candidate.id in chosenIds) continue
            val swapOutIndex = chosen.indexOfLast { other ->
                ratings[other.id] != RecipeRating.UP &&
                    usesStockedBulk(other, itemsById, stockOnHand) ==
                    usesStockedBulk(candidate, itemsById, stockOnHand) &&
                    dinnerFoodCost(other, settings, itemsById) ==
                    dinnerFoodCost(candidate, settings, itemsById)
            }
            if (swapOutIndex >= 0) {
                chosenIds.remove(chosen[swapOutIndex].id)
                chosen[swapOutIndex] = candidate
                chosenIds.add(candidate.id)
            }
        }
        var costed = cost(settings, chosen, itemsById, stockOnHand)
        val notes = mutableListOf<String>()
        val deferred = mutableListOf<String>()

        val cap = settings.weeklyBudgetCents
        if (cap > 0 && costed.cashNeededCents > cap) {
            // Step 1: defer Sam's restocks that blow the cap. Swap out every
            // dinner that needs a restocked Sam's item, name the restock, reprice.
            val restockItems = costed.lines
                .filter { it.store == Store.SAMS_CLUB && it.packsToBuy > 0 }
                .map { it.itemId }
            for (itemId in restockItems) {
                if (costed.cashNeededCents <= cap) break
                val item = itemsById[itemId] ?: continue
                val replacements = rotated.filter { cand ->
                    cand.id !in chosen.map { it.id } && !recipeUses(cand, itemId, itemsById)
                }.toMutableList()
                var swapped = false
                val iterator = chosen.listIterator()
                while (iterator.hasNext()) {
                    val current = iterator.next()
                    if (recipeUses(current, itemId, itemsById) && replacements.isNotEmpty()) {
                        iterator.set(replacements.first())
                        replacements.removeAt(0)
                        swapped = true
                    }
                }
                if (swapped) {
                    deferred += item.name
                    notes += "${item.name}: the ${Money(item.packPriceCents).format()} Sam's Club restock " +
                        "does not fit under this week's ${Money(cap).format()} budget, so it is moved to a " +
                        "later week and this week's dinners were swapped to ones that do not need it."
                    costed = cost(settings, chosen, itemsById, stockOnHand)
                }
            }
            // Step 2: greedily swap the most expensive dinner for the cheapest
            // unused candidate while still over the cap.
            var guard = 0
            while (costed.cashNeededCents > cap && guard < 50) {
                guard++
                val chosenIds = chosen.map { it.id }.toSet()
                val unused = rotated.filter { it.id !in chosenIds }
                if (unused.isEmpty() || chosen.isEmpty()) break
                val mostExpensive = chosen.maxByOrNull { dinnerFoodCost(it, settings, itemsById) } ?: break
                val cheapestUnused = unused.minByOrNull { dinnerFoodCost(it, settings, itemsById) } ?: break
                if (dinnerFoodCost(cheapestUnused, settings, itemsById) >= dinnerFoodCost(mostExpensive, settings, itemsById)) break
                chosen[chosen.indexOf(mostExpensive)] = cheapestUnused
                costed = cost(settings, chosen, itemsById, stockOnHand)
            }
            if (costed.cashNeededCents > cap) {
                notes += "Even the cheapest week Dinner Bot can build from these recipes costs " +
                    "${Money(costed.cashNeededCents).format()}, which is over the ${Money(cap).format()} " +
                    "weekly budget. Nothing was hidden: that is the real number."
            }
        }

        addPantryNotes(settings, chosen, pantryOwnedNames, notes)

        // A generated week NEVER repeats a dinner. When fewer different
        // dinners are available than there are cook nights, plan the nights
        // that can be filled and say so plainly instead of wrapping the
        // short list around (the v0.1.0 bug: index % size repeated dinners).
        val distinctChosen = chosen.distinctBy { it.id }
        if (distinctChosen.size < chosen.size) {
            costed = cost(settings, distinctChosen, itemsById, stockOnHand)
        }
        if (distinctChosen.size < nightsNeeded) {
            notes += "There are not enough different dinners available to fill every cook " +
                "night this week: ${distinctChosen.size} of $nightsNeeded nights are planned. " +
                "More recipes, or fewer won't-eat ingredients the dinners need, would fill the week."
        }
        val nights = settings.cookNights.zip(distinctChosen) { day, recipe ->
            PlannedNight(day = day, recipeId = recipe.id)
        }
        return WeekPlan(
            weekStart = weekStart,
            nights = nights,
            lines = costed.lines,
            cashNeededCents = costed.cashNeededCents,
            foodCostCents = costed.foodCostCents,
            samsMonthlySavingsCents = costed.gate.projectedMonthlySavingsCents,
            samsGateMet = costed.gate.useSams,
            deferredRestocks = deferred,
            notes = notes,
        )
    }

    /**
     * Swap one night's dinner for the best unused candidate, preferring swaps
     * that keep the week under the cash cap.
     *
     * [recipes] is the full adapted recipe set, used to resolve the dinners
     * already in the plan so repricing never drops one silently; replacement
     * candidates come from [candidatePool] (defaults to [recipes]), which is
     * where narrower rules (the diet presets' tag filters, Phase V2-2) apply.
     */
    fun swapDinner(
        settings: HouseholdSettings,
        current: WeekPlan,
        day: String,
        recipes: List<Recipe>,
        priceItems: List<PriceItem>,
        stockOnHand: Map<String, Quantity>,
        pantryOwnedNames: Set<String>,
        ratings: Map<String, RecipeRating> = emptyMap(),
        candidatePool: List<Recipe> = recipes,
    ): WeekPlan {
        val itemsById = priceItems.associateBy { it.id }
        val recipesById = recipes.associateBy { it.id }
        val nightIndex = current.nights.indexOfFirst { it.day == day }
        if (nightIndex < 0) return current
        // A night marked not cooking has no dinner to swap (Phase V2-2), and
        // its stored recipeId must not leak into the week's costing: only the
        // cooking nights are priced, and the trial slot is the target night's
        // position among them.
        if (current.nights[nightIndex].notCooking) return current
        val cookingIndexes = current.nights.indices.filter { !current.nights[it].notCooking }
        val trialSlot = cookingIndexes.indexOf(nightIndex)
        val inPlan = current.nights.filterNot { it.notCooking }.map { it.recipeId }.toSet()
        val candidates = rankCandidates(settings, candidatePool, itemsById, stockOnHand, ratings)
            .filter { it.id !in inPlan }
        if (candidates.isEmpty()) return current

        val baseChosen = cookingIndexes.mapNotNull { recipesById[current.nights[it].recipeId] }
        if (trialSlot < 0 || trialSlot >= baseChosen.size) return current
        for (candidate in candidates) {
            val trial = baseChosen.toMutableList()
            trial[trialSlot] = candidate
            val costed = cost(settings, trial, itemsById, stockOnHand)
            val cap = settings.weeklyBudgetCents
            if (cap <= 0 || costed.cashNeededCents <= cap) {
                val notes = current.notes.toMutableList()
                addPantryNotes(settings, trial, pantryOwnedNames, notes)
                return current.copy(
                    nights = current.nights.mapIndexed { i, night ->
                        if (i == nightIndex) night.copy(recipeId = candidate.id) else night
                    },
                    lines = costed.lines,
                    cashNeededCents = costed.cashNeededCents,
                    foodCostCents = costed.foodCostCents,
                    samsMonthlySavingsCents = costed.gate.projectedMonthlySavingsCents,
                    samsGateMet = costed.gate.useSams,
                    notes = notes.distinct(),
                )
            }
        }
        // No swap fits the cap: take the best candidate anyway and say so.
        val fallback = candidates.first()
        val trial = baseChosen.toMutableList()
        trial[trialSlot] = fallback
        val costed = cost(settings, trial, itemsById, stockOnHand)
        return current.copy(
            nights = current.nights.mapIndexed { i, night ->
                if (i == nightIndex) night.copy(recipeId = fallback.id) else night
            },
            lines = costed.lines,
            cashNeededCents = costed.cashNeededCents,
            foodCostCents = costed.foodCostCents,
            samsMonthlySavingsCents = costed.gate.projectedMonthlySavingsCents,
            samsGateMet = costed.gate.useSams,
            notes = (current.notes + "That swap puts the week at ${Money(costed.cashNeededCents).format()}, " +
                "over the ${Money(settings.weeklyBudgetCents).format()} budget; no cheaper swap was available.").distinct(),
        )
    }

    /**
     * Mark one night of a generated week not cooking, or put it back to
     * cooking (Phase V2-2). This is a per-night exception inside one stored
     * week, distinct from the setup cook-nights setting.
     *
     * Marking off takes that night's dinner, its ingredients, and its cash out
     * of the plan and the list at once: the week is repriced from the cooking
     * nights only, so an ingredient another cooking night also uses keeps
     * exactly that night's share. The night stays in the plan carrying its
     * [reason] ("leftovers", "eating out", or "other").
     *
     * Marking back on plans a dinner under the same rules [generate] and
     * [swapDinner] use: an unused candidate from the ranked pool, preferring
     * one that keeps the week under the cash cap, with the over-budget
     * fallback said plainly in the notes. When no dinner is available at all,
     * the night stays marked off and the note says why.
     */
    fun setNightCooking(
        settings: HouseholdSettings,
        current: WeekPlan,
        day: String,
        cooking: Boolean,
        reason: String? = null,
        recipes: List<Recipe>,
        priceItems: List<PriceItem>,
        stockOnHand: Map<String, Quantity>,
        pantryOwnedNames: Set<String>,
        ratings: Map<String, RecipeRating> = emptyMap(),
        candidatePool: List<Recipe> = recipes,
    ): WeekPlan {
        val itemsById = priceItems.associateBy { it.id }
        val recipesById = recipes.associateBy { it.id }
        val nightIndex = current.nights.indexOfFirst { it.day == day }
        if (nightIndex < 0) return current
        if (cooking == !current.nights[nightIndex].notCooking) return current

        if (!cooking) {
            val nights = current.nights.mapIndexed { i, night ->
                if (i == nightIndex) night.copy(notCooking = true, notCookingReason = reason) else night
            }
            val chosen = nights.filterNot { it.notCooking }.mapNotNull { recipesById[it.recipeId] }
            val costed = cost(settings, chosen, itemsById, stockOnHand)
            val notes = current.notes.toMutableList()
            addPantryNotes(settings, chosen, pantryOwnedNames, notes)
            return current.copy(
                nights = nights,
                lines = costed.lines,
                cashNeededCents = costed.cashNeededCents,
                foodCostCents = costed.foodCostCents,
                samsMonthlySavingsCents = costed.gate.projectedMonthlySavingsCents,
                samsGateMet = costed.gate.useSams,
                notes = notes.distinct(),
            )
        }

        // Turning the night back on: pick a dinner the week is not cooking.
        // Candidates come from the (possibly preset-narrowed) pool; the
        // dinners already cooking resolve from the full set above, so a
        // dinner a new preset would exclude is still priced while it is
        // displayed in this week.
        val cookingIds = current.nights.filterNot { it.notCooking }.map { it.recipeId }.toSet()
        val candidates = rankCandidates(settings, candidatePool, itemsById, stockOnHand, ratings)
            .filter { it.id !in cookingIds }
        if (candidates.isEmpty()) {
            return current.copy(
                notes = (current.notes + "No other dinner is available to cook that night, so it " +
                    "stays marked not cooking.").distinct(),
            )
        }
        val baseChosen = current.nights.filterNot { it.notCooking }
            .mapNotNull { recipesById[it.recipeId] }
        val cap = settings.weeklyBudgetCents
        for (candidate in candidates) {
            val trial = baseChosen + candidate
            val costed = cost(settings, trial, itemsById, stockOnHand)
            if (cap <= 0 || costed.cashNeededCents <= cap) {
                return restoredNight(current, nightIndex, candidate, trial, costed, settings, pantryOwnedNames)
            }
        }
        // No restored dinner fits the cap: take the best candidate anyway
        // and say so, the same fallback swapDinner uses.
        val fallback = candidates.first()
        val trial = baseChosen + fallback
        val costed = cost(settings, trial, itemsById, stockOnHand)
        val restored = restoredNight(current, nightIndex, fallback, trial, costed, settings, pantryOwnedNames)
        return restored.copy(
            notes = (restored.notes + "Cooking that night again puts the week at " +
                "${Money(costed.cashNeededCents).format()}, over the ${Money(cap).format()} budget; " +
                "no cheaper dinner was available.").distinct(),
        )
    }

    private fun restoredNight(
        current: WeekPlan,
        nightIndex: Int,
        dinner: Recipe,
        trial: List<Recipe>,
        costed: Costing.CostedWeek,
        settings: HouseholdSettings,
        pantryOwnedNames: Set<String>,
    ): WeekPlan {
        val notes = current.notes.toMutableList()
        addPantryNotes(settings, trial, pantryOwnedNames, notes)
        return current.copy(
            nights = current.nights.mapIndexed { i, night ->
                if (i == nightIndex) {
                    night.copy(recipeId = dinner.id, notCooking = false, notCookingReason = null)
                } else {
                    night
                }
            },
            lines = costed.lines,
            cashNeededCents = costed.cashNeededCents,
            foodCostCents = costed.foodCostCents,
            samsMonthlySavingsCents = costed.gate.projectedMonthlySavingsCents,
            samsGateMet = costed.gate.useSams,
            notes = notes.distinct(),
        )
    }

    private fun emptyPlan(
        weekStart: String,
        note: String,
    ): WeekPlan = WeekPlan(
        weekStart = weekStart,
        nights = emptyList(),
        lines = emptyList(),
        cashNeededCents = 0,
        foodCostCents = 0,
        samsMonthlySavingsCents = 0,
        samsGateMet = false,
        deferredRestocks = emptyList(),
        notes = listOf(note),
    )

    private fun cost(
        settings: HouseholdSettings,
        chosen: List<Recipe>,
        itemsById: Map<String, PriceItem>,
        stockOnHand: Map<String, Quantity>,
    ): Costing.CostedWeek = Costing.costRecipes(chosen, settings.householdSize, itemsById, stockOnHand)

    private fun dinnerFoodCost(recipe: Recipe, settings: HouseholdSettings, itemsById: Map<String, PriceItem>): Int =
        Costing.costRecipes(listOf(recipe), settings.householdSize, itemsById, emptyMap()).foodCostCents

    /**
     * Ranked candidates: DOWN-rated recipes removed first, then stock-covered
     * first, then cheapest, then thumbs-up, then title. The thumbs-up step is
     * a pure tie-break: it only orders recipes the earlier steps already tied,
     * so it can never put a pricier dinner ahead of a cheaper one.
     * The won't-eat rule is NOT applied here: callers pass the recipes that
     * [com.battlesbudz.dinnerbot.core.model.RecipeAdaptation] produced,
     * already stripped of removable won't-eat ingredients and with
     * essential-blocked recipes held out, so what arrives is plannable.
     */
    fun rankCandidates(
        settings: HouseholdSettings,
        recipes: List<Recipe>,
        itemsById: Map<String, PriceItem>,
        stockOnHand: Map<String, Quantity>,
        ratings: Map<String, RecipeRating> = emptyMap(),
    ): List<Recipe> {
        return recipes
            .filter { ratings[it.id] != RecipeRating.DOWN }
            .sortedWith(
                compareBy<Recipe>(
                    { recipe -> if (usesStockedBulk(recipe, itemsById, stockOnHand)) 0 else 1 },
                    { recipe -> dinnerFoodCost(recipe, settings, itemsById) },
                    { recipe -> if (ratings[recipe.id] == RecipeRating.UP) 0 else 1 },
                    { recipe -> recipe.title },
                )
            )
    }

    private fun usesStockedBulk(
        recipe: Recipe,
        itemsById: Map<String, PriceItem>,
        stockOnHand: Map<String, Quantity>,
    ): Boolean {
        val bulkIngredients = recipe.ingredients.mapNotNull { ingredient ->
            val id = ingredient.priceItemId?.let { Costing.substituteItemId(it) } ?: return@mapNotNull null
            val item = itemsById[id] ?: return@mapNotNull null
            if (item.homeStore == Store.SAMS_CLUB) id to ingredient.quantity else null
        }
        if (bulkIngredients.isEmpty()) return false
        return bulkIngredients.all { (id, qty) ->
            val onHand = stockOnHand[id]
            onHand != null && onHand.toBase() >= qty.toBase()
        }
    }

    private fun recipeUses(recipe: Recipe, itemId: String, itemsById: Map<String, PriceItem>): Boolean =
        recipe.ingredients.any { ingredient ->
            ingredient.priceItemId?.let { Costing.substituteItemId(it) } == itemId
        }

    private fun rotate(list: List<Recipe>, variation: Int): List<Recipe> {
        if (list.isEmpty()) return list
        val shift = ((variation % list.size) + list.size) % list.size
        return list.drop(shift) + list.take(shift)
    }

    private fun addPantryNotes(
        settings: HouseholdSettings,
        chosen: List<Recipe>,
        pantryOwnedNames: Set<String>,
        notes: MutableList<String>,
    ) {
        val owned = pantryOwnedNames.map { it.lowercase() }.toSet()
        val missing = chosen.flatMap { it.ingredients }
            .mapNotNull { it.pantryItem }
            .filter { it.lowercase() !in owned }
            .distinct()
        if (missing.isNotEmpty()) {
            notes += "Pantry staples these dinners use that are not marked owned: " +
                missing.joinToString(", ") + ". They are not priced in the price book, so they are not " +
                "on the shopping list; check the pantry before you shop."
        }
    }
}
