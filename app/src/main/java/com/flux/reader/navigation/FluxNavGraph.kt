package com.flux.reader.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.flux.reader.ui.theme.LocalFluxTokens
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.flux.reader.presentation.discover.DiscoverScreen
import com.flux.reader.presentation.library.LibraryScreen
import com.flux.reader.presentation.profile.ProfileScreen
import com.flux.reader.presentation.reader.ReaderScreen
import com.flux.reader.presentation.stats.StatsScreen

sealed class FluxDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector? = null,
    val unselectedIcon: ImageVector? = null
) {
    data object Library : FluxDestination("library", "Biblioteca", Icons.Filled.GridView, Icons.Outlined.GridView)
    data object Discover : FluxDestination("discover", "Explorar", Icons.Filled.Explore, Icons.Outlined.Explore)
    data object Stats : FluxDestination("stats", "Stats", Icons.Filled.BarChart, Icons.Outlined.BarChart)
    data object Profile : FluxDestination("profile", "Perfil", Icons.Filled.Person, Icons.Outlined.Person)
    data object Reader : FluxDestination("reader/{bookId}", "Lector") {
        fun createRoute(bookId: String) = "reader/$bookId"
    }
}

private val bottomDestinations = listOf(
    FluxDestination.Library,
    FluxDestination.Discover,
    FluxDestination.Stats,
    FluxDestination.Profile
)

@Composable
fun FluxNavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isReaderRoute = currentDestination?.route?.startsWith("reader/") == true

    var importTick by remember { mutableIntStateOf(0) }
    val nav: (FluxDestination) -> Unit = { destination ->
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (!isReaderRoute) {
                val t = LocalFluxTokens.current
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 16.dp, top = 28.dp)) {
                    // glass-dark, rounded-[2rem], h-72, borde --ui-border
                    Surface(
                        modifier = Modifier.widthIn(max = 448.dp).fillMaxWidth().height(72.dp).align(Alignment.Center),
                        shape = RoundedCornerShape(32.dp), color = if (t.dark) t.bgAccented else t.bg,
                        border = BorderStroke(1.dp, if (t.dark) Color.White.copy(alpha = 0.22f) else t.border), shadowElevation = 12.dp
                    ) {
                        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            @Composable
                            fun RowScope.Item(d: FluxDestination) {
                                val selected = currentDestination?.hierarchy?.any { it.route == d.route } == true
                                val c = if (selected) MaterialTheme.colorScheme.primary else t.text.copy(alpha = 0.78f)
                                Column(
                                    Modifier.weight(1f).fillMaxHeight().clickable(
                                        interactionSource = remember { MutableInteractionSource() }, indication = null
                                    ) { nav(d) },
                                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                                ) {
                                    // indicador de píldora: el seleccionado no depende solo del color
                                    Box(
                                        Modifier.clip(RoundedCornerShape(16.dp))
                                            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color.Transparent)
                                            .padding(horizontal = 14.dp, vertical = 3.dp)
                                    ) { Icon(if (selected) d.selectedIcon!! else d.unselectedIcon!!, null, tint = c, modifier = Modifier.size(24.dp)) }
                                    Spacer(Modifier.height(3.dp))
                                    Text(d.label, style = MaterialTheme.typography.labelMedium, color = c, maxLines = 1,
                                        fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium)
                                }
                            }
                            Item(FluxDestination.Library); Item(FluxDestination.Discover)
                            Spacer(Modifier.width(72.dp))
                            Item(FluxDestination.Stats); Item(FluxDestination.Profile)
                        }
                    }
                    // botón central "+" (size-16, borde de 6dp del color de fondo, sombra primary)
                    Box(
                        Modifier.align(Alignment.TopCenter).offset(y = (-28).dp).size(64.dp)
                            .shadow(16.dp, CircleShape, ambientColor = MaterialTheme.colorScheme.primary, spotColor = MaterialTheme.colorScheme.primary)
                            .border(6.dp, if (t.dark) t.bgAccented else t.bg, CircleShape).padding(6.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { nav(FluxDestination.Library); importTick++ },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Add, "Importar libro", tint = Color.White, modifier = Modifier.size(30.dp)) }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = FluxDestination.Library.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(FluxDestination.Library.route) {
                LibraryScreen(
                    importTick = importTick,
                    onOpenBook = { bookId ->
                        navController.navigate(FluxDestination.Reader.createRoute(bookId))
                    }
                )
            }
            composable(FluxDestination.Discover.route) {
                DiscoverScreen(
                    onOpenBook = { bookId ->
                        navController.navigate(FluxDestination.Reader.createRoute(bookId))
                    }
                )
            }
            composable(FluxDestination.Stats.route) {
                StatsScreen()
            }
            composable(FluxDestination.Profile.route) {
                ProfileScreen()
            }
            composable(
                route = FluxDestination.Reader.route,
                arguments = listOf(navArgument("bookId") { type = NavType.StringType })
            ) {
                ReaderScreen(onClose = { navController.popBackStack() })
            }
        }
    }
}
