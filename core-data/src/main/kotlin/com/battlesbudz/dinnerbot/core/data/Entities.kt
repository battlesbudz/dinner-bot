package com.battlesbudz.dinnerbot.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "price_items")
data class PriceItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val homeStore: String,
    val packDescription: String,
    val packAmount: Double,
    val packUnit: String,
    val packPriceCents: Int,
    val pricePerUnitCents: Double?,
    val checkedDate: String,
    val source: String,
    val userCorrected: Boolean,
)

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey val id: String,
    val title: String,
    val servings: Int,
    val activeMinutes: Int,
    val cuisine: String,
    val mainProtein: String,
    /** JSON-encoded List<String>. */
    val dietTagsJson: String,
    /** JSON-encoded List<RecipeIngredient>. */
    val ingredientsJson: String,
    /** JSON-encoded List<String>. */
    val stepsJson: String,
)

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val householdSize: Int,
    /** JSON-encoded List<String> of night codes, MON..SUN. */
    val cookNightsJson: String,
    val weeklyBudgetCents: Int,
    /** JSON-encoded List<String>. */
    val wontEatJson: String,
    val dietNotes: String,
    /** JSON-encoded List<String>. */
    val pantryStaplesJson: String,
    val onboardingComplete: Boolean,
)
