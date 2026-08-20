package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AppBottomNavigationBar
import com.example.ui.screens.LevelAndMistakesScreen
import com.example.ui.screens.MainChatScreen
import com.example.ui.screens.QuizScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
                val unresolvedMistakesCount by viewModel.unresolvedMistakesCount.collectAsStateWithLifecycle()
                val activeQuiz by viewModel.activeQuiz.collectAsStateWithLifecycle()

                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        topBar = {
                            if (currentScreen == AppScreen.SETTINGS) {
                                CenterAlignedTopAppBar(
                                    title = {
                                        Text(
                                            text = "الإعدادات والملفات",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    navigationIcon = {
                                        IconButton(onClick = { viewModel.navigateTo(AppScreen.CHAT) }) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "الرجوع للمحادثة"
                                            )
                                        }
                                    },
                                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                )
                            }
                        },
                        bottomBar = {
                            // Bottom navigation bar visible on main screens (hidden when taking active quiz)
                            if (activeQuiz == null) {
                                AppBottomNavigationBar(
                                    currentScreen = currentScreen,
                                    onNavigate = { viewModel.navigateTo(it) },
                                    unresolvedMistakesCount = unresolvedMistakesCount
                                )
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            Crossfade(
                                targetState = currentScreen,
                                label = "ScreenTransition"
                            ) { screen ->
                                when (screen) {
                                    AppScreen.CHAT -> {
                                        MainChatScreen(
                                            viewModel = viewModel,
                                            onNavigateToSettings = { viewModel.navigateTo(AppScreen.SETTINGS) }
                                        )
                                    }
                                    AppScreen.QUIZ -> {
                                        QuizScreen(
                                            viewModel = viewModel
                                        )
                                    }
                                    AppScreen.LEVEL_AND_MISTAKES -> {
                                        LevelAndMistakesScreen(
                                            viewModel = viewModel,
                                            onStartMistakesQuiz = {
                                                viewModel.startMistakesRetest()
                                                viewModel.navigateTo(AppScreen.QUIZ)
                                            }
                                        )
                                    }
                                    AppScreen.SETTINGS -> {
                                        SettingsScreen(
                                            viewModel = viewModel
                                        )
                                    }
                                }
                            }
                        }
                    }

                }
            }
        }
    }
}
