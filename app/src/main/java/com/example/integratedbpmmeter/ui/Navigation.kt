package com.example.integratedbpmmeter.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

private object Routes {
    const val Measure = "measure"
    const val TapBpm = "tap-bpm"
    const val FileAnalyze = "file-analyze"
    const val Library = "library"
    const val Settings = "settings"
}

private val topLevelRoutes = listOf(
    Routes.Measure,
    Routes.Library,
    Routes.Settings
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegratedBpmMeterApp(
    incomingAudioUri: Uri? = null,
    onIncomingAudioConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.hierarchy?.firstOrNull()?.route ?: Routes.Measure
    val canGoBack = currentRoute !in topLevelRoutes

    LaunchedEffect(incomingAudioUri) {
        if (incomingAudioUri != null) {
            navController.navigate(Routes.FileAnalyze) {
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        topBar = {
            if (canGoBack) {
                CenterAlignedTopAppBar(
                    title = { Text(titleForRoute(currentRoute)) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        },
        bottomBar = {
            CompactBottomNavigation {
                CompactNavItem(
                    selected = currentRoute == Routes.Measure || currentRoute == Routes.TapBpm || currentRoute == Routes.FileAnalyze,
                    icon = Icons.Filled.Speed,
                    label = "Measure",
                    onClick = {
                        navController.navigate(Routes.Measure) {
                            popUpTo(Routes.Measure)
                            launchSingleTop = true
                        }
                    }
                )
                CompactNavItem(
                    selected = currentRoute == Routes.Library,
                    icon = Icons.Filled.History,
                    label = "Library",
                    onClick = {
                        navController.navigate(Routes.Library) {
                            popUpTo(Routes.Measure)
                            launchSingleTop = true
                        }
                    }
                )
                CompactNavItem(
                    selected = currentRoute == Routes.Settings,
                    icon = Icons.Filled.Settings,
                    label = "Settings",
                    onClick = {
                        navController.navigate(Routes.Settings) {
                            popUpTo(Routes.Measure)
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.Measure,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.Measure) {
                NowPlayingScreen(
                    onFileAnalyze = { navController.navigate(Routes.FileAnalyze) }
                )
            }
            composable(Routes.TapBpm) {
                TapBpmScreen()
            }
            composable(Routes.FileAnalyze) {
                FileAnalyzeScreen(
                    incomingAudioUri = incomingAudioUri,
                    onIncomingAudioConsumed = onIncomingAudioConsumed
                )
            }
            composable(Routes.Library) {
                HistoryScreen()
            }
            composable(Routes.Settings) {
                SettingsScreen()
            }
        }
    }
}

@Composable
private fun CompactBottomNavigation(content: @Composable RowScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                content = content
            )
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsBottomHeight(WindowInsets.navigationBars)
            )
        }
    }
}

@Composable
private fun RowScope.CompactNavItem(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val iconColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    )
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = iconColor
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = textColor
            )
        }
    }
}
private fun titleForRoute(route: String): String {
    return when (route) {
        Routes.Measure -> "Measure"
        Routes.TapBpm -> "Tap BPM"
        Routes.FileAnalyze -> "File Tap"
        Routes.Library -> "Library"
        Routes.Settings -> "Settings"
        else -> "Bpm Now"
    }
}
