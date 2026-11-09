package com.mcclabs.gimmer.nutrition

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.withTransaction
import com.mcclabs.gimmer.R
import com.mcclabs.gimmer.data.WorkoutDatabase
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

enum class MealType(val label: Int) {
    BREAKFAST(R.string.breakfast),
    LUNCH(R.string.lunch),
    DINNER(R.string.dinner),
    PRE_WORKOUT(R.string.pre_workout),
    POST_WORKOUT(R.string.post_workout),
    SNACK(R.string.snack),
}

@Entity(tableName = "food_entries", indices = [Index("foodName")])
data class FoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foodName: String,
    val caloriesPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val barcode: String? = null,
) {
    val isValid: Boolean
        get() = foodName.isNotBlank() && foodName.length <= 200 &&
            (barcode == null || barcode.matches(BarcodePattern)) &&
            caloriesPer100g in 0.0..1000.0 &&
            listOf(proteinPer100g, carbsPer100g, fatPer100g).all { it in 0.0..100.0 } &&
            proteinPer100g + carbsPer100g + fatPer100g <= 101.0

    fun nutrientsFor(grams: Double): Nutrients {
        val multiplier = grams / 100.0
        return Nutrients(
            caloriesPer100g * multiplier,
            proteinPer100g * multiplier,
            carbsPer100g * multiplier,
            fatPer100g * multiplier,
        )
    }
}

@Entity(
    tableName = "meal_logs",
    foreignKeys = [ForeignKey(
        entity = FoodEntry::class,
        parentColumns = ["id"],
        childColumns = ["foodEntryId"],
        onDelete = ForeignKey.RESTRICT,
    )],
    indices = [Index("foodEntryId"), Index("dateTimestamp")],
)
data class MealLog(
    @PrimaryKey val id: String,
    val foodEntryId: Long,
    val mealType: MealType,
    val consumedGrams: Double,
    val dateTimestamp: Long,
)

data class LoggedMeal(
    @Embedded val log: MealLog,
    @Relation(parentColumn = "foodEntryId", entityColumn = "id") val food: FoodEntry,
) {
    val nutrients: Nutrients get() = food.nutrientsFor(log.consumedGrams)
}

data class Nutrients(
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
) {
    operator fun plus(other: Nutrients) = Nutrients(
        calories + other.calories,
        protein + other.protein,
        carbs + other.carbs,
        fat + other.fat,
    )
}

internal val BarcodePattern = Regex("[0-9]{4,24}")

internal fun validConsumedGrams(value: Double) = value > 0.0 && value <= 10_000.0

internal fun Iterable<LoggedMeal>.totals(): Nutrients =
    fold(Nutrients()) { sum, meal -> sum + meal.nutrients }

internal fun LoggedMeal.requireValid() {
    require(food.isValid && validConsumedGrams(log.consumedGrams))
    require(log.dateTimestamp > 0)
    require(java.util.UUID.fromString(log.id).toString() == log.id)
}

@Dao
interface NutritionDao {
    @Transaction
    @Query("""
        SELECT * FROM meal_logs WHERE dateTimestamp >= :start AND dateTimestamp < :end
        ORDER BY dateTimestamp, id
    """)
    fun observeDay(start: Long, end: Long): Flow<List<LoggedMeal>>

    @Transaction
    @Query("SELECT * FROM meal_logs ORDER BY dateTimestamp, id")
    suspend fun allMeals(): List<LoggedMeal>

    @Query("""
        SELECT food_entries.* FROM food_entries
        INNER JOIN (SELECT foodEntryId, MAX(dateTimestamp) AS lastUsed FROM meal_logs GROUP BY foodEntryId) AS recent
        ON food_entries.id = recent.foodEntryId
        ORDER BY recent.lastUsed DESC, food_entries.id DESC LIMIT 20
    """)
    fun observeRecentFoods(): Flow<List<FoodEntry>>

    @Query("""
        SELECT id FROM food_entries
        WHERE foodName = :name AND caloriesPer100g = :calories AND proteinPer100g = :protein
        AND carbsPer100g = :carbs AND fatPer100g = :fat AND barcode IS :barcode LIMIT 1
    """)
    suspend fun matchingFood(
        name: String, calories: Double, protein: Double, carbs: Double, fat: Double, barcode: String?,
    ): Long?

    @Query("SELECT id FROM meal_logs")
    suspend fun allMealIds(): List<String>

    @Insert
    suspend fun insertFood(food: FoodEntry): Long

    @Insert
    suspend fun insertMeal(log: MealLog)

    @Query("DELETE FROM meal_logs WHERE id = :id")
    suspend fun deleteMeal(id: String)
}

internal class NutritionRepository(private val database: WorkoutDatabase) {
    private val dao = database.nutritionDao()
    val recentFoods = dao.observeRecentFoods()

    fun observeDay(date: LocalDate, zone: ZoneId) = dao.observeDay(
        date.atStartOfDay(zone).toInstant().toEpochMilli(),
        date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
    )

    suspend fun save(meal: LoggedMeal) = database.withTransaction { insert(meal) }

    suspend fun delete(id: String) = dao.deleteMeal(id)

    // Called inside the same transaction as the workout and bodyweight restore.
    suspend fun merge(meals: List<LoggedMeal>): Int {
        if (meals.isEmpty()) return 0
        val existingIds = dao.allMealIds().toMutableSet()
        val newMeals = meals.filter { existingIds.add(it.log.id) }
        newMeals.forEach { insert(it) }
        return newMeals.size
    }

    private suspend fun insert(meal: LoggedMeal) {
        meal.requireValid()
        val food = meal.food
        // A changed product label gets a new row so it cannot rewrite historical totals.
        val foodId = dao.matchingFood(
            food.foodName, food.caloriesPer100g, food.proteinPer100g, food.carbsPer100g,
            food.fatPer100g, food.barcode,
        ) ?: dao.insertFood(food.copy(id = 0))
        dao.insertMeal(meal.log.copy(foodEntryId = foodId))
    }
}
