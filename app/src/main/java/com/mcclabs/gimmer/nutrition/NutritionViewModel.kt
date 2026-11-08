package com.mcclabs.gimmer.nutrition

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mcclabs.gimmer.data.WorkoutDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

internal data class NutritionTargets(
    val calories: Int = 2800,
    val protein: Int = 160,
    val carbs: Int = 360,
    val fat: Int = 80,
) {
    val isValid: Boolean get() = calories in 1..20_000 && listOf(protein, carbs, fat).all { it in 1..2_000 }
}

internal data class NutritionDay(
    val date: LocalDate,
    val today: LocalDate,
    val meals: List<LoggedMeal>? = null,
) {
    val totals: Nutrients = meals.orEmpty().totals()
}

internal data class FoodEditor(val food: FoodEntry? = null, val brands: String? = null)

internal data class FoodSearchState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<FoodSearchResult> = emptyList(),
    val notice: String? = null,
    val editor: FoodEditor? = null,
    val saving: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
internal class NutritionViewModel(application: Application, private val savedState: SavedStateHandle) :
    AndroidViewModel(application) {
    private val repository = NutritionRepository(WorkoutDatabase.getInstance(application))
    private val foodClient by lazy { OpenFoodFactsClient.instance }
    private val preferences = application.getSharedPreferences("nutrition_targets", Context.MODE_PRIVATE)
    private val initialTargets = NutritionTargets()

    private val targetState = MutableStateFlow(
        NutritionTargets(
            calories = preferences.getInt("calories", initialTargets.calories),
            protein = preferences.getInt("protein", initialTargets.protein),
            carbs = preferences.getInt("carbs", initialTargets.carbs),
            fat = preferences.getInt("fat", initialTargets.fat),
        ).takeIf { it.isValid } ?: initialTargets,
    )
    val targets = targetState.asStateFlow()

    private val selectedDate = savedState.getStateFlow<String?>(SelectedDateKey, null)
    private val clock = flow {
        while (true) {
            val zone = ZoneId.systemDefault()
            emit(LocalDate.now(zone) to zone)
            delay(15_000)
        }
    }.distinctUntilChanged()
    private val initialDate = LocalDate.now()
    val day = combine(clock, selectedDate) { (today, zone), saved ->
        val date = saved?.let(LocalDate::parse)?.coerceAtMost(today) ?: today
        NutritionDay(date, today) to zone
    }.flatMapLatest { (day, zone) ->
        repository.observeDay(day.date, zone).map { day.copy(meals = it) }.onStart { emit(day) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
        NutritionDay(selectedDate.value?.let(LocalDate::parse)?.coerceAtMost(initialDate) ?: initialDate, initialDate))

    val recentFoods = repository.recentFoods
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val searchState = MutableStateFlow(FoodSearchState())
    val search = searchState.asStateFlow()
    private val events = Channel<String>(Channel.BUFFERED)
    val messages = events.receiveAsFlow()
    private var searchJob: Job? = null

    fun selectDate(date: LocalDate) {
        val today = LocalDate.now()
        if (date <= today) savedState[SelectedDateKey] = date.toString().takeUnless { date == today }
    }

    fun saveTargets(value: NutritionTargets) {
        if (!value.isValid) return
        preferences.edit {
            putInt("calories", value.calories)
            putInt("protein", value.protein)
            putInt("carbs", value.carbs)
            putInt("fat", value.fat)
        }
        targetState.value = value
    }

    fun resetSearch() {
        searchJob?.cancel()
        searchState.value = FoodSearchState()
    }

    fun changeQuery(query: String) {
        searchJob?.cancel()
        searchState.value = FoodSearchState(query = query.take(120))
    }

    fun submitSearch() {
        val query = searchState.value.query.trim()
        if (query.length < 2 || searchState.value.loading) return
        if (query.matches(BarcodePattern)) {
            loadProduct(query)
            return
        }
        fetchFood {
            val results = foodClient.search(query)
            it.copy(results = results, notice = "Eşleşen besin bulunamadı.".takeIf { results.isEmpty() })
        }
    }

    fun loadProduct(barcode: String) = fetchFood {
        when (val result = foodClient.lookup(barcode)) {
            is ProductLookup.Found -> it.copy(editor = FoodEditor(result.food, result.brands))
            ProductLookup.Missing -> it.copy(notice = "Bu barkoda ait ürün bulunamadı.")
            ProductLookup.UnusableNutrition -> it.copy(notice =
                "Bu ürünün doğrulanabilir 100 g değerleri eksik. Etiketteki 100 g değerlerini elle girebilirsin.")
        }
    }

    private fun fetchFood(request: suspend (FoodSearchState) -> FoodSearchState) {
        searchJob?.cancel()
        val state = searchState.value.copy(loading = true, notice = null)
        searchState.value = state
        searchJob = viewModelScope.launch {
            try {
                searchState.value = request(state).copy(loading = false)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val message = if (error is HttpException && error.code() == 429) {
                    "Çok fazla arama yapıldı. Biraz bekleyip tekrar dene."
                } else {
                    "Besin kataloğuna ulaşılamadı. Bağlantını kontrol edip tekrar dene."
                }
                searchState.value = state.copy(loading = false, notice = message)
            }
        }
    }

    fun openEditor(food: FoodEntry? = null) {
        searchJob?.cancel()
        searchState.update { it.copy(loading = false, editor = FoodEditor(food)) }
    }

    fun closeEditor() {
        if (!searchState.value.saving) searchState.update { it.copy(editor = null) }
    }

    fun saveFood(food: FoodEntry, mealType: MealType, date: LocalDate, grams: Double, onSaved: () -> Unit) {
        if (searchState.value.saving || !food.isValid || !validConsumedGrams(grams)) return
        searchState.update { it.copy(saving = true) }
        val timestamp = ZonedDateTime.now().with(date).toInstant().toEpochMilli()
        val meal = MealLog(UUID.randomUUID().toString(), 0, mealType, grams, timestamp)

        viewModelScope.launch {
            try {
                repository.save(LoggedMeal(meal, food))
                searchState.update { it.copy(editor = null) }
                events.send("Besin kaydedildi.")
                onSaved()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                events.send("Besin kaydedilemedi. Tekrar dene.")
            } finally {
                searchState.update { it.copy(saving = false) }
            }
        }
    }

    fun deleteMeal(id: String) {
        viewModelScope.launch {
            try {
                repository.delete(id)
                events.send("Besin kaydı silindi.")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                events.send("Besin kaydı silinemedi. Tekrar dene.")
            }
        }
    }

    private companion object {
        const val SelectedDateKey = "nutrition_date"
    }
}
