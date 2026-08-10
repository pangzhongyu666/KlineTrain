package com.klinetrain.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.klinetrain.app.data.model.TrainingMode
import com.klinetrain.app.ui.formula.FormulaScreen
import com.klinetrain.app.ui.home.HomeScreen
import com.klinetrain.app.ui.profile.ProfileScreen
import com.klinetrain.app.ui.records.RecordDetailScreen
import com.klinetrain.app.ui.records.RecordsScreen
import com.klinetrain.app.ui.season.SeasonDataScreen
import com.klinetrain.app.ui.settings.ChartSettingsScreen
import com.klinetrain.app.ui.setup.TrainingSetupScreen
import com.klinetrain.app.ui.setup.TrainingSettingsScreen
import com.klinetrain.app.ui.strategy.StrategyScreen
import com.klinetrain.app.ui.training.TrainingScreen

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "main") {
        composable("main") {
            MainTabs(
                onStartTraining = { mode -> nav.navigate("setup/${mode.name}") },
                onOpenRecords = { nav.navigate("records") },
                onOpenRecordDetail = { id -> nav.navigate("record/$id") },
                onOpenStrategies = { nav.navigate("strategies") },
                onOpenFormulas = { nav.navigate("formulas") },
                onOpenTrainSettings = { nav.navigate("trainSettings") },
                onOpenChartSettings = { nav.navigate("chartSettings") },
                onOpenSeasonData = { nav.navigate("seasonData") }
            )
        }
        composable(
            "setup/{mode}",
            arguments = listOf(navArgument("mode") { type = NavType.StringType })
        ) { entry ->
            val mode = TrainingMode.valueOf(entry.arguments?.getString("mode") ?: TrainingMode.BLIND.name)
            TrainingSetupScreen(
                mode = mode,
                onStart = {
                    nav.navigate("training/${mode.name}") {
                        popUpTo("main")
                    }
                },
                onBack = { nav.popBackStack() }
            )
        }
        composable(
            "training/{mode}",
            arguments = listOf(navArgument("mode") { type = NavType.StringType })
        ) { entry ->
            val mode = TrainingMode.valueOf(entry.arguments?.getString("mode") ?: TrainingMode.BLIND.name)
            TrainingScreen(mode = mode, onExit = { nav.popBackStack() })
        }
        composable("records") {
            RecordsScreen(
                onBack = { nav.popBackStack() },
                onOpenDetail = { id -> nav.navigate("record/$id") }
            )
        }
        composable(
            "record/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            RecordDetailScreen(
                recordId = entry.arguments?.getLong("id") ?: 0L,
                onBack = { nav.popBackStack() }
            )
        }
        composable("strategies") {
            StrategyScreen(onBack = { nav.popBackStack() })
        }
        composable("formulas") {
            FormulaScreen(onBack = { nav.popBackStack() })
        }
        composable("trainSettings") {
            TrainingSettingsScreen(onBack = { nav.popBackStack() })
        }
        composable("chartSettings") {
            ChartSettingsScreen(onBack = { nav.popBackStack() })
        }
        composable("seasonData") {
            SeasonDataScreen(
                onBack = { nav.popBackStack() },
                onOpenRecord = { id -> nav.navigate("record/$id") }
            )
        }
    }
}

@Composable
private fun MainTabs(
    onStartTraining: (TrainingMode) -> Unit,
    onOpenRecords: () -> Unit,
    onOpenRecordDetail: (Long) -> Unit,
    onOpenStrategies: () -> Unit,
    onOpenFormulas: () -> Unit,
    onOpenTrainSettings: () -> Unit,
    onOpenChartSettings: () -> Unit,
    onOpenSeasonData: () -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.EmojiEvents, contentDescription = "训练") },
                    label = { Text("训练") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Person, contentDescription = "我的") },
                    label = { Text("我的") }
                )
            }
        }
    ) { padding ->
        val modifier = Modifier.padding(padding)
        if (tab == 0) {
            HomeScreen(
                onStartTraining = onStartTraining,
                onOpenRecords = onOpenRecords,
                onOpenRecordDetail = onOpenRecordDetail,
                modifier = modifier
            )
        } else {
            ProfileScreen(
                onOpenStrategies = onOpenStrategies,
                onOpenFormulas = onOpenFormulas,
                onOpenRecords = onOpenRecords,
                onOpenTrainSettings = onOpenTrainSettings,
                onOpenChartSettings = onOpenChartSettings,
                onOpenSeasonData = onOpenSeasonData,
                modifier = modifier
            )
        }
    }
}
