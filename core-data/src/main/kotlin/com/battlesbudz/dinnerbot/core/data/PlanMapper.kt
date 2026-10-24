package com.battlesbudz.dinnerbot.core.data

import com.battlesbudz.dinnerbot.core.model.GroceryLine
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PlannedNight
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Store
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Pure conversions between plan/grocery model types and Room entities. Unit-testable on the JVM. */
object PlanMapper {
    private val json = Json { ignoreUnknownKeys = true }
    private val nightList = ListSerializer(PlannedNight.serializer())
    private val stringList = ListSerializer(String.serializer())

    /** The persisted shape of a generated week plan. */
    data class StoredPlan(
        val id: String,
        val weekStart: String,
        val budgetCents: Int,
        val cashNeededCents: Int,
        val foodCostCents: Int,
        val samsMonthlySavingsCents: Int,
        val nights: List<PlannedNight>,
        val notes: List<String>,
        val createdAtMillis: Long,
    )

    fun StoredPlan.toEntity(): PlanEntity = PlanEntity(
        id = id,
        weekStart = weekStart,
        budgetCents = budgetCents,
        cashNeededCents = cashNeededCents,
        foodCostCents = foodCostCents,
        samsMonthlySavingsCents = samsMonthlySavingsCents,
        nightsJson = json.encodeToString(nightList, nights),
        notesJson = json.encodeToString(stringList, notes),
        createdAtMillis = createdAtMillis,
    )

    fun PlanEntity.toStoredPlan(): StoredPlan = StoredPlan(
        id = id,
        weekStart = weekStart,
        budgetCents = budgetCents,
        cashNeededCents = cashNeededCents,
        foodCostCents = foodCostCents,
        samsMonthlySavingsCents = samsMonthlySavingsCents,
        nights = json.decodeFromString(nightList, nightsJson),
        notes = json.decodeFromString(stringList, notesJson),
        createdAtMillis = createdAtMillis,
    )

    fun GroceryLine.toEntity(planId: String): GroceryItemEntity = GroceryItemEntity(
        planId = planId,
        itemId = itemId,
        displayName = displayName,
        store = store.name,
        neededAmount = needed.amount,
        neededUnit = needed.unit.name,
        packsToBuy = packsToBuy,
        packPriceCents = estimatedPackPriceCents,
        portionCostCents = portionCostCents,
        checkedOff = checkedOff,
        reconciliationAdded = reconciliationAdded,
        substitutedForItemId = substitutedForItemId,
    )

    fun GroceryItemEntity.toModel(): GroceryLine = GroceryLine(
        itemId = itemId,
        displayName = displayName,
        store = Store.valueOf(store),
        needed = Quantity(neededAmount, MeasureUnit.valueOf(neededUnit)),
        packsToBuy = packsToBuy,
        estimatedPackPriceCents = packPriceCents,
        portionCostCents = portionCostCents,
        checkedOff = checkedOff,
        reconciliationAdded = reconciliationAdded,
        substitutedForItemId = substitutedForItemId,
    )
}
