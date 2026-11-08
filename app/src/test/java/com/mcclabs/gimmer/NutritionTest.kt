package com.mcclabs.gimmer

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.mcclabs.gimmer.data.BackupCodec
import com.mcclabs.gimmer.nutrition.ApiProduct
import com.mcclabs.gimmer.nutrition.FoodEntry
import com.mcclabs.gimmer.nutrition.FoodSearchApi
import com.mcclabs.gimmer.nutrition.FoodSearchResponse
import com.mcclabs.gimmer.nutrition.LoggedMeal
import com.mcclabs.gimmer.nutrition.MealLog
import com.mcclabs.gimmer.nutrition.MealType
import com.mcclabs.gimmer.nutrition.OpenFoodFactsApi
import com.mcclabs.gimmer.nutrition.OpenFoodFactsClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class NutritionTest {
    private val barcode = "3017620422003"
    private val food = FoodEntry(foodName = "Yoğurt", caloriesPer100g = 75.0, proteinPer100g = 4.1,
        carbsPer100g = 5.7, fatPer100g = 4.0)

    @Test fun textSearchReadsMultipleBrandsAndMissingBrandFields() = runBlocking {
        val client = OpenFoodFactsClient(
            searchApi = object : FoodSearchApi {
                override suspend fun search(query: String, languages: List<String>, pageSize: Int, fields: String) =
                    Gson().fromJson("""{"hits":[
                        {"code":"86920292","product_name":"Bisküvi","brands":["Eti","Marka"]},
                        {"code":"8691720001242","product_name":"Diğer Bisküvi"}
                    ]}""", FoodSearchResponse::class.java)
            },
            productApi = object : OpenFoodFactsApi {
                override suspend fun product(barcode: String, fields: String) = error("Unexpected barcode lookup")
            },
        )
        val result = client.search("bisküvi")
        assertEquals(listOf("Eti, Marka", null), result.map { it.brands })
    }

    @Test fun apiRequiresExplicitGramBasedSourceEvenWhenAggregatedSetSays100g() {
        val productJson = """
            {
              "code": "$barcode", "product_name": "Hazelnut spread", "product_name_tr": "Fındık kreması",
              "nutrition": {"aggregated_set": {"per": "100g", "preparation": "as_sold", "nutrients": {
                "energy-kcal": {"value": 539, "unit": "kcal", "source_per": "100g"},
                "proteins": {"value": 6.3, "unit": "g", "source_per": "100g"},
                "carbohydrates": {"value": 57.5, "unit": "g", "source_per": "100g"},
                "fat": {"value": 30.9, "unit": "g", "source_per": "100g"}
              }}}
            }
        """.trimIndent()
        fun parse(json: String) = Gson().fromJson(json, ApiProduct::class.java).toFoodEntry(barcode)
        val product = checkNotNull(parse(productJson))
        assertEquals("Fındık kreması", product.foodName)
        assertEquals(134.75, product.nutrientsFor(25.0).calories, 0.000001)
        assertEquals(1.575, product.nutrientsFor(25.0).protein, 0.000001)
        assertNull(parse(productJson.replace("\"source_per\": \"100g\"", "\"source_per\": \"100ml\"")))
        assertNull(parse(productJson.replace("\"value\": 30.9", "\"value\": null")))
        val energyInKilojoules = productJson.replace(
            """"energy-kcal": {"value": 539, "unit": "kcal"""",
            """"energy-kj": {"value": 2255.176, "unit": "kJ"""",
        )
        assertEquals(539.0, checkNotNull(parse(energyInKilojoules)).caloriesPer100g, 0.000001)
    }

    @Test fun nutritionExportGroupsByLocalDayAndRoundTripsUnroundedPortions() {
        fun meal(grams: Double, time: String) = LoggedMeal(
            MealLog(UUID.randomUUID().toString(), 0, MealType.SNACK, grams, Instant.parse(time).toEpochMilli()),
            food,
        )
        val records = listOf(
            meal(12.3456, "2026-11-08T20:59:00Z"),
            meal(150.5, "2026-11-08T21:00:00Z"),
            meal(25.0, "2026-11-09T07:30:00Z"),
        )
        val zone = ZoneId.of("Europe/Istanbul")
        val json = BackupCodec.encode(emptyList(), meals = records, zone = zone)
        val root = JsonParser.parseString(json).asJsonObject
        val days = root.getAsJsonArray("nutrition")
        assertEquals(2, days.size())
        assertEquals("2026-11-08", days[0].asJsonObject.get("date").asString)
        val secondDay = days[1].asJsonObject
        assertEquals("2026-11-09", secondDay.get("date").asString)
        assertEquals(2, secondDay.getAsJsonArray("mealLogs").size())
        assertEquals(131.625, secondDay.get("totalCalories").asDouble, 0.000001)
        assertEquals(7.1955, secondDay.get("totalProtein").asDouble, 0.000001)
        assertEquals(10.0035, secondDay.get("totalCarbs").asDouble, 0.000001)
        assertEquals(7.02, secondDay.get("totalFat").asDouble, 0.000001)
        assertEquals(records, BackupCodec.decodeSnapshot(json).meals)
        root.remove("nutrition")
        assertTrue(BackupCodec.decodeSnapshot(root.toString()).meals.isEmpty())
    }
}
