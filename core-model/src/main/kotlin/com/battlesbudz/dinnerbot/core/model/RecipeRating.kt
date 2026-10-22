package com.battlesbudz.dinnerbot.core.model

import kotlinx.serialization.Serializable

/**
 * A thumbs rating for one recipe. One rating per recipe; the latest tap
 * wins. No rating stored at all means unrated, and unrated recipes stay in
 * normal rotation.
 *
 * DOWN takes the recipe out of planner generation and swaps entirely until
 * the rating changes; the recipe stays in the library and can be rated again
 * at any time. UP keeps the recipe in rotation and makes it win planner ties
 * (same stock coverage, same dinner cost) over unrated recipes. UP never
 * overrides cost, budget, or the won't-eat rule.
 */
@Serializable
enum class RecipeRating { UP, DOWN }
