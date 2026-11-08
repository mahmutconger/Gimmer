package com.mcclabs.gimmer.nutrition

import com.google.gson.JsonArray
import java.time.Instant
import java.time.ZoneId

internal fun exportNutrition(meals: List<LoggedMeal>, zone: ZoneId): List<Map<String, Any>> =
    meals.groupBy { Instant.ofEpochMilli(it.log.dateTimestamp).atZone(zone).toLocalDate() }
        .toSortedMap()
        .map { (date, dayMeals) ->
            val total = dayMeals.totals()
            mapOf(
                "date" to date.toString(),
                "totalCalories" to total.calories,
                "totalProtein" to total.protein,
                "totalCarbs" to total.carbs,
                "totalFat" to total.fat,
                "mealLogs" to dayMeals.sortedBy { it.log.dateTimestamp }.map { meal ->
                    val (log, food) = meal
                    val nutrients = meal.nutrients
                    mapOf(
                        "id" to log.id,
                        "foodName" to food.foodName,
                        "barcode" to food.barcode,
                        "mealType" to log.mealType.name,
                        "consumedGrams" to log.consumedGrams,
                        "dateTimestamp" to log.dateTimestamp,
                        "caloriesPer100g" to food.caloriesPer100g,
                        "proteinPer100g" to food.proteinPer100g,
                        "carbsPer100g" to food.carbsPer100g,
                        "fatPer100g" to food.fatPer100g,
                        "calories" to nutrients.calories,
                        "protein" to nutrients.protein,
                        "carbs" to nutrients.carbs,
                        "fat" to nutrients.fat,
                    )
                },
            )
        }

internal fun decodeNutrition(days: JsonArray): List<LoggedMeal> {
    val meals = buildList {
        for (day in days) {
            val logs = day.asJsonObject.getAsJsonArray("mealLogs")
            require(logs.size() <= 50_000 - size)
            for (element in logs) {
                val row = element.asJsonObject
                val food = FoodEntry(
                    foodName = row.get("foodName").asString,
                    caloriesPer100g = row.get("caloriesPer100g").asDouble,
                    proteinPer100g = row.get("proteinPer100g").asDouble,
                    carbsPer100g = row.get("carbsPer100g").asDouble,
                    fatPer100g = row.get("fatPer100g").asDouble,
                    barcode = row.get("barcode")?.takeUnless { it.isJsonNull }?.asString,
                )
                val log = MealLog(
                    id = row.get("id").asString,
                    foodEntryId = 0,
                    mealType = MealType.valueOf(row.get("mealType").asString),
                    consumedGrams = row.get("consumedGrams").asDouble,
                    dateTimestamp = row.get("dateTimestamp").asBigDecimal.longValueExact(),
                )
                add(LoggedMeal(log, food).also { it.requireValid() })
            }
        }
    }
    require(meals.map { it.log.id }.distinct().size == meals.size)
    return meals
}
