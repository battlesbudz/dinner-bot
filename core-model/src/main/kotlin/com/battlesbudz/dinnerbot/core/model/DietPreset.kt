package com.battlesbudz.dinnerbot.core.model

import kotlinx.serialization.Serializable

/**
 * A named diet preset (Phase V2-2, scope A8): a bundle expressed entirely in
 * Dinner Bot's existing machinery, not a new rule engine.
 *
 * - [wontEatEntries] join the household's own won't-eat list before
 *   [RecipeAdaptation] runs, so the essential / removable handling applies
 *   dish by dish exactly as it does to the household's own entries.
 * - [excludedTags] keep any recipe carrying one of those diet tags out of
 *   generated weeks.
 * - [requiredTag], when set, keeps only recipes carrying that diet tag in
 *   generated weeks.
 *
 * Presets are picked in setup or settings and combine with the household's
 * own entries. They only ever add filtering on top of the household's rules;
 * they never remove a household entry, and they never touch the standing
 * rules. As the app already states for the won't-eat list: presets are
 * planning filters, not medical or allergen guarantees.
 */
@Serializable
data class DietPreset(
    val id: String,
    val name: String,
    val summary: String,
    val wontEatEntries: List<String> = emptyList(),
    val excludedTags: List<String> = emptyList(),
    val requiredTag: String? = null,
)

@Serializable
data class DietPresetsFile(val version: Int, val presets: List<DietPreset>)

/** Pure composition of the household's settings with the selected presets. */
object DietPresets {

    /** The presets the household has switched on, in definition order. */
    fun selected(settings: HouseholdSettings, all: List<DietPreset>): List<DietPreset> {
        val wanted = settings.dietPresets.map { it.trim().lowercase() }.toSet()
        return all.filter { it.id.trim().lowercase() in wanted }
    }

    /**
     * The won't-eat list the planner actually sees: the household's own
     * entries first (they always win and are never removed), then every
     * selected preset's entries, deduplicated ignoring case.
     */
    fun effectiveWontEat(settings: HouseholdSettings, all: List<DietPreset>): List<String> {
        val seen = mutableSetOf<String>()
        val merged = mutableListOf<String>()
        for (entry in settings.wontEat + selected(settings, all).flatMap { it.wontEatEntries }) {
            val trimmed = entry.trim()
            if (trimmed.isNotEmpty() && seen.add(trimmed.lowercase())) merged += trimmed
        }
        return merged
    }

    /**
     * Tag half of the bundle: a recipe stays eligible when it carries none
     * of the selected presets' excluded tags and, for every selected preset
     * that names a required tag, it carries that tag too.
     */
    fun tagEligible(recipe: Recipe, selectedPresets: List<DietPreset>): Boolean {
        if (selectedPresets.isEmpty()) return true
        val tags = recipe.dietTags.map { it.trim().lowercase() }.toSet()
        for (preset in selectedPresets) {
            if (preset.excludedTags.any { it.trim().lowercase() in tags }) return false
            val required = preset.requiredTag?.trim()?.lowercase()
            if (!required.isNullOrEmpty() && required !in tags) return false
        }
        return true
    }

    /** The tag-filtered planning pool for the selected presets. */
    fun applyTagFilters(recipes: List<Recipe>, selectedPresets: List<DietPreset>): List<Recipe> =
        recipes.filter { tagEligible(it, selectedPresets) }
}
