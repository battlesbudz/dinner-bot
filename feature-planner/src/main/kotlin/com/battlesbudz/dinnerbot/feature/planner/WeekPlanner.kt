package com.battlesbudz.dinnerbot.feature.planner

import com.battlesbudz.dinnerbot.core.model.Costing
import com.battlesbudz.dinnerbot.core.model.GroceryLine
import com.battlesbudz.dinnerbot.core.model.HouseholdSettings
import com.battlesbudz.dinnerbot.core.model.Money
import com.battlesbudz.dinnerbot.core.model.PlannedNight
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Recipe
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
    ): WeekPlan {
        val itemsById = priceItems.associateBy { it.id }
        val candidates = rankCandidates(settings, recipes, itemsById, stockOnHand)
        val nightsNeeded = settings.cookNights.size
        if (candidates.isEmpty() || nightsNeeded == 0) {
            return emptyPlan(
                weekStart,
                note = "No dinners are available to plan yet. Check the won't-eat list in Settings.",
            )
        }

        // Rotate the ranked list for Regenerate so a new variation is a new week.
        val rotated = rotate(candidates, variation)
        var chosen = rotated.take(nightsNeeded).toMutableList()
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
     */
    fun swapDinner(
        settings: HouseholdSettings,
        current: WeekPlan,
        day: String,
        recipes: List<Recipe>,
        priceItems: List<PriceItem>,
        stockOnHand: Map<String, Quantity>,
        pantryOwnedNames: Set<String>,
    ): WeekPlan {
        val itemsById = priceItems.associateBy { it.id }
        val recipesById = recipes.associateBy { it.id }
        val nightIndex = current.nights.indexOfFirst { it.day == day }
        if (nightIndex < 0) return current
        val inPlan = current.nights.map { it.recipeId }.toSet()
        val candidates = rankCandidates(settings, recipes, itemsById, stockOnHand)
            .filter { it.id !in inPlan }
        if (candidates.isEmpty()) return current

        val baseChosen = current.nights.mapNotNull { recipesById[it.recipeId] }
        for (candidate in candidates) {
            val trial = baseChosen.toMutableList()
            if (nightIndex >= trial.size) break
            trial[nightIndex] = candidate
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
        trial[nightIndex] = fallback
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
     * Ranked candidates: stock-covered first, then cheapest, then title.
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
    ): List<Recipe> {
        return recipes.sortedWith(
            compareBy<Recipe>(
                { recipe -> if (usesStockedBulk(recipe, itemsById, stockOnHand)) 0 else 1 },
                { recipe -> dinnerFoodCost(recipe, settings, itemsById) },
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
