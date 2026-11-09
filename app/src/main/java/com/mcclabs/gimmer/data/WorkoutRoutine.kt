package com.mcclabs.gimmer.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import kotlinx.coroutines.flow.Flow
import java.util.Locale

@Entity(tableName = "workout_routine")
data class WorkoutRoutine(@PrimaryKey val id: Int = 1, val json: String)

@Dao
interface WorkoutRoutineDao {
    @Query("SELECT * FROM workout_routine WHERE id = 1")
    fun observe(): Flow<WorkoutRoutine?>

    @Query("SELECT * FROM workout_routine WHERE id = 1")
    suspend fun get(): WorkoutRoutine?

    @Upsert
    suspend fun replace(routine: WorkoutRoutine)
}

internal object RoutineCodec {
    const val MAX_JSON_LENGTH = 200_000
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).setPrettyPrinting().create()
    private val identifier = Regex("[a-zA-Z0-9_-]{1,80}")
    private val repetitionTarget = Regex("([1-9][0-9]{0,3})(?:[-–]([1-9][0-9]{0,3}))?( sn)?")
    private val turkish = Locale.forLanguageTag("tr-TR")

    fun encode(days: List<WorkoutDay>): String = gson.toJson(toJson(days))

    fun toJson(days: List<WorkoutDay>): JsonObject = gson.toJsonTree(
        mapOf("routineVersion" to 1, "days" to days.map { day ->
            mapOf("id" to day.id, "name" to day.name, "programName" to day.programName,
                "exercises" to day.exercises.map { exercise ->
                    mapOf("id" to exercise.id, "name" to exercise.displayName,
                        "targetSets" to exercise.targetSets, "targetReps" to exercise.targetReps,
                        "muscleGroup" to exercise.muscleGroup, "isCompound" to exercise.isCompound,
                        "isBarbell" to exercise.isBarbell)
                }, "pairs" to day.pairs.map { listOf(it.firstId, it.secondId) })
        }),
    ).asJsonObject

    fun decode(json: String): List<WorkoutDay> {
        require(json.length in 1..MAX_JSON_LENGTH) { "Program JSON’u boş veya çok büyük." }
        val root = requireNotNull(gson.fromJson(json, JsonObject::class.java)) { "Geçerli bir JSON nesnesi gir." }
        return decode(if (root.has("routineVersion")) root else {
            requireNotNull(root.getAsJsonObject("routine")) { "JSON içinde bir antrenman programı bulunamadı." }
        })
    }

    fun decode(root: JsonObject): List<WorkoutDay> {
        require(root.integer("routineVersion") == 1) { "Bu program biçimi desteklenmiyor." }
        val days = root.getAsJsonArray("days")
        require(days != null && days.size() in 1..7) { "Programda 1 ile 7 arasında gün olmalı." }
        val dayIds = mutableSetOf<String>()
        val dayNames = mutableSetOf<String>()
        val exercisesById = mutableMapOf<String, Exercise>()
        val exerciseIdsByName = mutableMapOf<String, String>()
        return days.map { entry ->
            val day = entry.asJsonObject
            val id = day.id()
            val name = day.text("name", 100)
            require(dayIds.add(id) && dayNames.add(name.lowercase(turkish))) { "Program günleri benzersiz olmalı." }
            val exercises = day.getAsJsonArray("exercises")
            require(exercises != null && exercises.size() in 1..40) { "Her günde 1 ile 40 arasında hareket olmalı." }
            val dayExerciseIds = mutableSetOf<String>()
            val decodedExercises = exercises.map { item ->
                val row = item.asJsonObject
                val exerciseId = row.id()
                require(dayExerciseIds.add(exerciseId)) { "Aynı hareket bir güne iki kez eklenemez." }
                val catalog = WorkoutCatalog.exercisesById[exerciseId]
                val inputName = row.text("name", 200)
                val displayName = if (catalog?.name == inputName) catalog.displayName else inputName
                val targetSets = row.integer("targetSets")
                require(targetSets in 1..100) { "Hedef set sayısı 1 ile 100 arasında olmalı." }
                val reps = row.text("targetReps", 20)
                val range = repetitionTarget.matchEntire(reps)
                val lower = range?.groupValues?.get(1)?.toIntOrNull()
                val upper = range?.groupValues?.get(2)?.toIntOrNull() ?: lower
                require(lower != null && upper != null && lower <= upper && upper <= 1000) {
                    "Tekrar hedefini 8, 8-12 veya 30-45 sn biçiminde gir."
                }
                val exercise = Exercise(exerciseId, catalog?.name ?: displayName, targetSets,
                    reps.replace('–', '-'), row.text("muscleGroup", 100),
                    row.boolean("isCompound"), row.boolean("isBarbell"), displayName)
                val previous = exercisesById.putIfAbsent(exerciseId, exercise)
                require(previous == null || previous.copy(targetSets = exercise.targetSets,
                    targetReps = exercise.targetReps) == exercise) {
                    "Aynı hareketin adı, kas grubu ve türü program boyunca tutarlı olmalı."
                }
                val priorId = exerciseIdsByName.putIfAbsent(displayName.lowercase(turkish), exerciseId)
                require(priorId == null || priorId == exerciseId) { "Farklı hareketler aynı adı kullanamaz." }
                exercise
            }
            val pairedIds = mutableSetOf<String>()
            val pairs = day.getAsJsonArray("pairs")?.map { entry ->
                val pair = entry.asJsonArray
                require(pair.size() == 2 && pair.all { it.isJsonPrimitive && it.asJsonPrimitive.isString }) {
                    "Her çift iki hareket kimliği içermeli."
                }
                val first = pair[0].asString
                val second = pair[1].asString
                require(first in dayExerciseIds && second in dayExerciseIds &&
                    pairedIds.add(first) && pairedIds.add(second)) {
                    "Hareket çiftleri aynı günden olmalı ve bir hareket yalnızca bir çifte girebilir."
                }
                first to second
            }.orEmpty()
            WorkoutDay(id, name, day.text("programName", 100), decodedExercises).withPairs(*pairs.toTypedArray())
        }
    }

    private fun JsonObject.id(): String =
        text("id", 80).also { require(identifier.matches(it)) { "Hareket ve gün kimlikleri geçersiz." } }

    private fun JsonObject.text(key: String, maxLength: Int): String {
        val field = get(key)
        require(field?.isJsonPrimitive == true && field.asJsonPrimitive.isString) { "Eksik veya geçersiz metin alanı." }
        return field.asString.trim().also { require(it.length in 1..maxLength) { "Metin alanı boş veya çok uzun." } }
    }

    private fun JsonObject.integer(key: String): Int {
        val field = get(key)
        require(field?.isJsonPrimitive == true && field.asJsonPrimitive.isNumber) { "Geçerli bir sayı alanı gerekli." }
        return field.asBigDecimal.intValueExact()
    }

    private fun JsonObject.boolean(key: String): Boolean {
        val field = get(key)
        require(field?.isJsonPrimitive == true && field.asJsonPrimitive.isBoolean) { "Hareket türü ve bar bilgisi gerekli." }
        return field.asBoolean
    }
}
