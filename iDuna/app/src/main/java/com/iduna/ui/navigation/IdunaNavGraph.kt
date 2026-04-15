package com.iduna.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.iduna.IdunaViewModel
import com.iduna.domain.model.BleConnectionState
import com.iduna.ui.components.StatusDot
import com.iduna.ui.screens.AlertsScreen
import com.iduna.ui.screens.DashboardScreen
import com.iduna.ui.screens.DeviceConnectionScreen
import com.iduna.ui.screens.EmergencySosScreen
import com.iduna.ui.screens.GraphScreen
import com.iduna.ui.screens.HistoryScreen
import com.iduna.ui.screens.ProfileScreen
import com.iduna.ui.screens.ReportsScreen
import com.iduna.ui.screens.SettingsScreen
import com.iduna.ui.screens.SplashScreen
import com.iduna.ui.theme.AccentBlue
import com.iduna.ui.theme.AccentGreen
import com.iduna.ui.theme.AccentRed
import kotlinx.coroutines.launch

private object RootRoute {
    const val Splash = "splash"
    const val Connection = "connection"
    const val Main = "main"
    const val Sos = "sos"
}

data class MainDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val mainDestinations = listOf(
    MainDestination("dashboard", "Dashboard", Icons.Outlined.Dashboard),
    MainDestination("graph", "Live Graph", Icons.Outlined.BarChart),
    MainDestination("history", "History", Icons.Outlined.BarChart),
    MainDestination("alerts", "Alerts", Icons.Outlined.Notifications),
    MainDestination("reports", "Reports", Icons.Outlined.Description),
    MainDestination("profile", "Profile", Icons.Outlined.Person),
    MainDestination("settings", "Settings", Icons.Outlined.Settings),
)

@Composable
fun IdunaNavGraph(
    viewModel: IdunaViewModel,
) {
    val rootNavController = rememberNavController()
    val rootBackStack by rootNavController.currentBackStackEntryAsState()
    val dashboardState by viewModel.dashboardState.collectAsStateWithLifecycle()
    val sosState by viewModel.sosState.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()

    LaunchedEffect(dashboardState.connectionState, rootBackStack?.destination?.route) {
        if (
            dashboardState.connectionState == BleConnectionState.Connected &&
            rootBackStack?.destination?.route == RootRoute.Connection
        ) {
            rootNavController.navigate(RootRoute.Main) {
                popUpTo(RootRoute.Connection) { inclusive = true }
            }
        }
    }

    LaunchedEffect(sosState, rootBackStack?.destination?.route) {
        if (sosState != null && rootBackStack?.destination?.route != RootRoute.Sos) {
            rootNavController.navigate(RootRoute.Sos)
        } else if (sosState == null && rootBackStack?.destination?.route == RootRoute.Sos) {
            rootNavController.popBackStack()
        }
    }

    NavHost(
        navController = rootNavController,
        startDestination = RootRoute.Splash,
    ) {
        composable(RootRoute.Splash) {
            SplashScreen(
                onFinished = {
                    rootNavController.navigate(RootRoute.Connection) {
                        popUpTo(RootRoute.Splash) { inclusive = true }
                    }
                },
            )
        }
        composable(RootRoute.Connection) {
            DeviceConnectionScreen(
                dashboardState = dashboardState,
                onScan = viewModel::startScan,
                onStopScan = viewModel::stopScan,
                onReconnect = viewModel::reconnect,
            )
        }
        composable(RootRoute.Main) {
            MainShell(
                viewModel = viewModel,
                onDisconnect = {
                    viewModel.disconnect()
                    rootNavController.navigate(RootRoute.Connection) {
                        popUpTo(RootRoute.Main) { inclusive = true }
                    }
                },
            )
        }
        composable(RootRoute.Sos) {
            val activeSos = sosState ?: return@composable
            EmergencySosScreen(
                sosState = activeSos,
                profile = profile,
                onCancel = viewModel::cancelSos,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(
    viewModel: IdunaViewModel,
    onDisconnect: () -> Unit,
) {
    val innerNavController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by innerNavController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: mainDestinations.first().route
    val currentDestination = mainDestinations.firstOrNull { it.route == currentRoute } ?: mainDestinations.first()
    val dashboardState by viewModel.dashboardState.collectAsStateWithLifecycle()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = "iDuna",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                )
                mainDestinations.forEach { destination ->
                    NavigationDrawerItem(
                        label = { Text(destination.label) },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        selected = currentRoute == destination.route,
                        onClick = {
                            innerNavController.navigate(destination.route) {
                                popUpTo(innerNavController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
                NavigationDrawerItem(
                    label = { Text("Disconnect Device") },
                    icon = { Icon(Icons.Outlined.Notifications, contentDescription = null) },
                    selected = false,
                    onClick = {
                        onDisconnect()
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(text = currentDestination.label) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Outlined.Dashboard, contentDescription = "Open navigation")
                        }
                    },
                    actions = {
                        StatusDot(
                            label = dashboardState.connectionState.name.replace("_", " "),
                            color = when (dashboardState.connectionState) {
                                BleConnectionState.Connected -> AccentGreen
                                BleConnectionState.Connecting,
                                BleConnectionState.Scanning,
                                -> AccentBlue

                                else -> AccentRed
                            },
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                    ),
                )
            },
        ) { paddingValues ->
            MainContentNavHost(
                navController = innerNavController,
                viewModel = viewModel,
                modifier = Modifier.padding(paddingValues),
            )
        }
    }
}

@Composable
private fun MainContentNavHost(
    navController: NavHostController,
    viewModel: IdunaViewModel,
    modifier: Modifier = Modifier,
) {
    val dashboardState by viewModel.dashboardState.collectAsStateWithLifecycle()
    val historySummary by viewModel.historySummary.collectAsStateWithLifecycle()
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = "dashboard",
        modifier = modifier,
    ) {
        composable("dashboard") {
            DashboardScreen(
                dashboardState = dashboardState,
                onGraphClick = { navController.navigate("graph") },
                onReportsClick = { navController.navigate("reports") },
                onSettingsClick = { navController.navigate("settings") },
            )
        }
        composable("graph") {
            GraphScreen(dashboardState = dashboardState)
        }
        composable("history") {
            HistoryScreen(
                historySummary = historySummary,
                onRangeSelected = viewModel::setHistoryRange,
            )
        }
        composable("alerts") {
            AlertsScreen(alerts = alerts)
        }
        composable("reports") {
            ReportsScreen(viewModel = viewModel)
        }
        composable("profile") {
            ProfileScreen(
                profile = profile,
                onSave = viewModel::updateProfile,
            )
        }
        composable("settings") {
            SettingsScreen(
                settings = settings,
                onSettingsChanged = viewModel::updateSetting,
            )
        }
    }
}
