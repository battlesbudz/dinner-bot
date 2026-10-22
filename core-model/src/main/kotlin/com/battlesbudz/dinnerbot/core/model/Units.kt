package com.battlesbudz.dinnerbot.core.model

import kotlinx.serialization.Serializable

enum class Dimension { WEIGHT, VOLUME, COUNT }

/**
 * Closed unit system. Each unit knows its dimension and its factor to the
 * dimension's base unit (ounce for weight, fluid ounce for volume, count).
 */
@Serializable
enum class MeasureUnit(val dimension: Dimension, val toBaseFactor: Double, val label: String) {
    OUNCE(Dimension.WEIGHT, 1.0, "oz"),
    POUND(Dimension.WEIGHT, 16.0, "lb"),
    GRAM(Dimension.WEIGHT, 0.035274, "g"),
    KILOGRAM(Dimension.WEIGHT, 35.274, "kg"),
    FLUID_OUNCE(Dimension.VOLUME, 1.0, "fl oz"),
    CUP(Dimension.VOLUME, 8.0, "cup"),
    TABLESPOON(Dimension.VOLUME, 0.5, "tbsp"),
    TEASPOON(Dimension.VOLUME, 0.5 / 3.0, "tsp"),
    MILLILITER(Dimension.VOLUME, 0.033814, "ml"),
    LITER(Dimension.VOLUME, 33.814, "L"),
    COUNT(Dimension.COUNT, 1.0, "ct"),
    EACH(Dimension.COUNT, 1.0, "each"),
}

@Serializable
data class Quantity(val amount: Double, val unit: MeasureUnit) {
    fun toBase(): Double = amount * unit.toBaseFactor

    /** Convert to another unit of the same dimension. */
    fun convertTo(target: MeasureUnit): Quantity {
        require(target.dimension == unit.dimension) {
            "cannot convert ${unit.label} (${unit.dimension}) to ${target.label} (${target.dimension})"
        }
        return Quantity(toBase() / target.toBaseFactor, target)
    }

    operator fun plus(other: Quantity): Quantity {
        require(other.unit.dimension == unit.dimension) { "cannot add different dimensions" }
        return Quantity(amount + other.convertTo(unit).amount, unit)
    }

    operator fun times(factor: Double): Quantity = Quantity(amount * factor, unit)

    companion object {
        fun pounds(amount: Double) = Quantity(amount, MeasureUnit.POUND)
        fun ounces(amount: Double) = Quantity(amount, MeasureUnit.OUNCE)
        fun count(amount: Double) = Quantity(amount, MeasureUnit.COUNT)
        fun cups(amount: Double) = Quantity(amount, MeasureUnit.CUP)
    }
}
