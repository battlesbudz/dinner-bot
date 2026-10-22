package com.battlesbudz.dinnerbot.core.model

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * Money is always integer cents. No floating point money anywhere in Dinner Bot.
 */
@Serializable
data class Money(val cents: Int) : Comparable<Money> {
    operator fun plus(other: Money): Money = Money(cents + other.cents)
    operator fun minus(other: Money): Money = Money(cents - other.cents)
    operator fun times(count: Int): Money = Money(cents * count)
    operator fun times(factor: Double): Money = Money((cents * factor).roundToInt())

    /** Portion cost of [quantityUsed] out of a pack: price scaled by the fraction used. */
    fun portion(quantityUsed: Double, packQuantity: Double): Money {
        require(packQuantity > 0.0) { "pack quantity must be positive" }
        return Money((cents * quantityUsed / packQuantity).roundToInt())
    }

    fun format(): String {
        val sign = if (cents < 0) "-" else ""
        val abs = kotlin.math.abs(cents)
        val dollars = abs / 100
        val remainder = (abs % 100).toString().padStart(2, '0')
        return "$sign\$$dollars.$remainder"
    }

    override fun compareTo(other: Money): Int = cents.compareTo(other.cents)

    companion object {
        val ZERO = Money(0)
        fun dollars(value: Double): Money = Money((value * 100).roundToInt())
    }
}
