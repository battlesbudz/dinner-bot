package com.battlesbudz.dinnerbot.core.data

import com.battlesbudz.dinnerbot.core.model.CookCheckoff
import com.battlesbudz.dinnerbot.core.model.RanOutEvent
import com.battlesbudz.dinnerbot.core.model.RanOutSource
import com.battlesbudz.dinnerbot.core.model.RanOutState

/** Pure conversions between the Sunday reconciliation model types and Room entities. */
object ReconciliationMapper {

    fun RanOutEvent.toEntity(): RanOutEventEntity = RanOutEventEntity(
        id = id,
        itemKey = itemKey,
        displayName = displayName,
        stapleSide = stapleSide,
        priceItemId = priceItemId,
        bulkItemId = bulkItemId,
        pantryStapleName = pantryStapleName,
        dateMarked = dateMarked,
        source = source.name,
        state = state.name,
        cyclePlanId = cyclePlanId,
    )

    fun RanOutEventEntity.toModel(): RanOutEvent = RanOutEvent(
        id = id,
        itemKey = itemKey,
        displayName = displayName,
        stapleSide = stapleSide,
        priceItemId = priceItemId,
        bulkItemId = bulkItemId,
        pantryStapleName = pantryStapleName,
        dateMarked = dateMarked,
        source = runCatching { RanOutSource.valueOf(source) }.getOrDefault(RanOutSource.COOKING),
        state = runCatching { RanOutState.valueOf(state) }.getOrDefault(RanOutState.PENDING),
        cyclePlanId = cyclePlanId,
    )

    fun CookCheckoff.toEntity(): CookCheckoffEntity = CookCheckoffEntity(
        planId = planId,
        day = day,
        itemKey = itemKey,
        checked = checked,
    )

    fun CookCheckoffEntity.toModel(): CookCheckoff = CookCheckoff(
        planId = planId,
        day = day,
        itemKey = itemKey,
        checked = checked,
    )
}
