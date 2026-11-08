package com.mcclabs.gimmer.nutrition

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

private const val SearchFields = "code,product_name,brands"
private const val ProductFields = "code,product_name,product_name_tr,brands,nutrition"

internal interface FoodSearchApi {
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("langs") languages: List<String> = listOf("tr", "en"),
        @Query("page_size") pageSize: Int = 20,
        @Query("fields") fields: String = SearchFields,
    ): FoodSearchResponse
}

internal interface OpenFoodFactsApi {
    @GET("api/v3.6/product/{barcode}")
    suspend fun product(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String = ProductFields,
    ): ProductResponse
}

internal data class FoodSearchResponse(val hits: List<FoodSearchHit?>?)
internal data class FoodSearchHit(
    @SerializedName("code") val barcode: String?,
    @SerializedName("product_name") val name: String?,
    val brands: List<String?>?,
)

internal data class FoodSearchResult(val barcode: String, val name: String, val brands: String?)
internal data class ProductResponse(val product: ApiProduct?)

internal data class ApiProduct(
    @SerializedName("code") val barcode: String?,
    @SerializedName("product_name") val name: String?,
    @SerializedName("product_name_tr") val turkishName: String?,
    val brands: String?,
    val nutrition: ApiNutrition?,
) {
    fun toFoodEntry(requestedBarcode: String): FoodEntry? {
        val set = nutrition?.aggregatedSet ?: return null
        if (set.per != "100g" || set.preparation != "as_sold") return null

        val values = set.nutrients.orEmpty()
        val calories = values["energy-kcal"]?.valueIn("kcal")
            ?: values["energy-kj"]?.valueIn("kJ")?.div(4.184)
            ?: return null
        val protein = values["proteins"]?.valueIn("g") ?: return null
        val carbs = values["carbohydrates"]?.valueIn("g") ?: return null
        val fat = values["fat"]?.valueIn("g") ?: return null
        val foodName = listOf(turkishName, name).firstOrNull { !it.isNullOrBlank() }?.trim()
            ?: return null
        val code = barcode?.takeIf { it.matches(BarcodePattern) } ?: requestedBarcode
        return FoodEntry(foodName = foodName, caloriesPer100g = calories, proteinPer100g = protein,
            carbsPer100g = carbs, fatPer100g = fat, barcode = code).takeIf { it.isValid }
    }
}

internal data class ApiNutrition(@SerializedName("aggregated_set") val aggregatedSet: ApiNutrientSet?)
internal data class ApiNutrientSet(
    val per: String?,
    val preparation: String?,
    val nutrients: Map<String, ApiNutrient?>?,
)

internal data class ApiNutrient(
    val value: Double?,
    val unit: String?,
    @SerializedName("source_per") val sourcePer: String?,
    val modifier: String?,
) {
    // Some products report an aggregated per of 100g even when the source label uses 100ml.
    fun valueIn(expectedUnit: String): Double? = value?.takeIf {
        unit == expectedUnit && sourcePer == "100g" &&
            (modifier.isNullOrEmpty() || modifier == "~") && it.isFinite() && it >= 0
    }
}

internal sealed interface ProductLookup {
    data class Found(val food: FoodEntry, val brands: String?) : ProductLookup
    data object Missing : ProductLookup
    data object UnusableNutrition : ProductLookup
}

internal class OpenFoodFactsClient(
    private val searchApi: FoodSearchApi,
    private val productApi: OpenFoodFactsApi,
) {
    suspend fun search(query: String): List<FoodSearchResult> {
        val escaped = buildString {
            for (character in query) {
                if (character in LuceneSpecialCharacters) append('\\')
                append(character)
            }
        }
        return searchApi.search(escaped).hits.orEmpty().mapNotNull { hit ->
            val code = hit?.barcode?.takeIf { it.matches(BarcodePattern) } ?: return@mapNotNull null
            val name = hit.name?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val brands = hit.brands.orEmpty().filterNotNull().filter { it.isNotBlank() }.joinToString(", ")
            FoodSearchResult(code, name, brands.takeIf { it.isNotEmpty() })
        }.distinctBy { it.barcode }
    }

    suspend fun lookup(barcode: String): ProductLookup {
        val product = try {
            productApi.product(barcode).product
        } catch (error: HttpException) {
            if (error.code() == 404) return ProductLookup.Missing
            throw error
        } ?: return ProductLookup.Missing
        val food = product.toFoodEntry(barcode) ?: return ProductLookup.UnusableNutrition
        return ProductLookup.Found(food, product.brands?.takeIf { it.isNotBlank() })
    }

    companion object {
        private const val LuceneSpecialCharacters = "+-!(){}[]^\"~*?:\\/|&"
        val instance: OpenFoodFactsClient by lazy {
            val client = OkHttpClient.Builder()
                .callTimeout(15, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", "Gimmer/3.0 (Android; com.mcclabs.gimmer)")
                        .build()
                    chain.proceed(request)
                }
                .build()

            fun retrofit(baseUrl: String) = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            OpenFoodFactsClient(
                retrofit("https://search.openfoodfacts.org/").create(FoodSearchApi::class.java),
                retrofit("https://world.openfoodfacts.org/").create(OpenFoodFactsApi::class.java),
            )
        }
    }
}
