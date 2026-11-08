package com.mcclabs.gimmer.ui

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mcclabs.gimmer.nutrition.FoodSearchScreen
import com.mcclabs.gimmer.nutrition.MealType
import com.mcclabs.gimmer.nutrition.NutritionScreen
import com.mcclabs.gimmer.nutrition.NutritionViewModel
import java.time.LocalDate

private enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    Workout("workout", "Antrenman", Icons.Rounded.FitnessCenter),
    Timer("timer", "Zamanlayıcı", Icons.Rounded.Timer),
    Nutrition("nutrition", "Beslenme", Icons.Rounded.Restaurant),
    Progress("progress", "Gelişim", Icons.AutoMirrored.Rounded.TrendingUp),
    Settings("settings", "Ayarlar", Icons.Rounded.Settings),
}

private const val EXERCISE_HISTORY = "progress/detail/{name}"
private const val FOOD_SEARCH = "nutrition/food/{meal}/{date}"

@Composable
fun GimmerApp(viewModel: WorkoutViewModel = viewModel()) {
    val nutrition: NutritionViewModel = viewModel()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(nutrition) {
        nutrition.messages.collect { snackbar.showSnackbar(it) }
    }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        modifier = Modifier.fillMaxSize().imePadding(),
        bottomBar = {
            if (currentRoute != EXERCISE_HISTORY && currentRoute != FOOD_SEARCH && !imeVisible) {
                GimmerNavigation(currentRoute, viewModel.restTimer) { destination ->
                    if (currentRoute != destination.route) {
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            }
        },
    ) { contentPadding ->
        val direction = LocalLayoutDirection.current
        NavHost(
            navController = navController,
            startDestination = Destination.Workout.route,
            modifier = Modifier.padding(
                start = contentPadding.calculateStartPadding(direction),
                top = contentPadding.calculateTopPadding(),
                end = contentPadding.calculateEndPadding(direction),
            ).consumeWindowInsets(contentPadding),
        ) {
            composable(Destination.Workout.route) { WorkoutScreen(viewModel, contentPadding.calculateBottomPadding()) }
            composable(Destination.Timer.route) {
                RestTimerScreen(viewModel.restTimer, contentPadding.calculateBottomPadding())
            }
            composable(Destination.Nutrition.route) {
                NutritionScreen(nutrition, contentPadding.calculateBottomPadding()) { meal, date ->
                    nutrition.resetSearch()
                    navController.navigate("nutrition/food/${meal.name}/$date") { launchSingleTop = true }
                }
            }
            composable(FOOD_SEARCH, arguments = listOf(navArgument("meal") { type = NavType.StringType },
                navArgument("date") { type = NavType.StringType })) { entry ->
                val meal = MealType.valueOf(requireNotNull(entry.arguments?.getString("meal")))
                val date = LocalDate.parse(requireNotNull(entry.arguments?.getString("date")))
                FoodSearchScreen(nutrition, meal, date, contentPadding.calculateBottomPadding()) {
                    navController.popBackStack()
                }
            }
            composable(Destination.Settings.route) { SettingsScreen(viewModel, contentPadding.calculateBottomPadding()) }
            composable(Destination.Progress.route) {
                ProgressScreen(viewModel, contentPadding.calculateBottomPadding()) { name ->
                    navController.navigate("progress/detail/${Uri.encode(name)}")
                }
            }
            composable(EXERCISE_HISTORY, arguments = listOf(navArgument("name") { type = NavType.StringType })) {
                val name = requireNotNull(it.arguments?.getString("name"))
                ExerciseHistoryScreen(name, viewModel, contentPadding.calculateBottomPadding()) {
                    navController.popBackStack()
                }
            }
        }
    }
}

@Composable
private fun GimmerNavigation(currentRoute: String?, timer: RestTimer, onSelect: (Destination) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxWidth().background(
            Brush.verticalGradient(listOf(Color.Transparent, colors.background)),
        ).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = colors.surfaceContainerHigh,
            border = BorderStroke(1.dp, colors.outlineVariant),
            shadowElevation = 12.dp,
        ) {
            Row(
                Modifier.fillMaxWidth().selectableGroup().padding(horizontal = 5.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Destination.entries.forEach { destination ->
                    val selected = currentRoute == destination.route
                    val tint = if (selected) colors.primary else colors.onSurfaceVariant
                    Column(
                        modifier = Modifier.weight(1f).clip(MaterialTheme.shapes.large)
                            .selectable(selected, role = Role.Tab, onClick = { onSelect(destination) })
                            .padding(horizontal = 2.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Surface(shape = CircleShape, color = if (selected) colors.primary else colors.surfaceContainerHigh,
                            contentColor = if (selected) colors.onPrimary else tint) {
                            Box(Modifier.size(width = 44.dp, height = 34.dp), contentAlignment = Alignment.Center) {
                                if (destination == Destination.Timer) {
                                    TimerNavigationIcon(timer)
                                } else {
                                    Icon(destination.icon, contentDescription = null, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                        Text(destination.label, color = tint, style = MaterialTheme.typography.labelSmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimerNavigationIcon(timer: RestTimer) {
    val active by timer.active.collectAsStateWithLifecycle(false)
    val finished by timer.finished.collectAsStateWithLifecycle()
    BadgedBox(badge = {
        if (active || finished) Badge(
            containerColor = if (finished) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
        )
    }, modifier = Modifier.semantics {
        if (active || finished) stateDescription = if (active) "Çalışıyor" else "Dinlenme tamamlandı"
    }) {
        Icon(Icons.Rounded.Timer, contentDescription = null, modifier = Modifier.size(20.dp))
    }
}
