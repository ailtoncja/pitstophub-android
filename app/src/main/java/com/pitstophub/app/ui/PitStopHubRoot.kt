package com.pitstophub.app.ui

import com.pitstophub.app.ui.categories.RulesScreen
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.pitstophub.app.AppContainer
import com.pitstophub.app.R
import com.pitstophub.app.data.model.RaceStatus
import com.pitstophub.app.domain.RaceTime
import com.pitstophub.app.ui.account.AccountEvent
import com.pitstophub.app.ui.account.AccountController
import com.pitstophub.app.ui.account.AlertsSheet
import com.pitstophub.app.ui.account.AuthSheet
import com.pitstophub.app.ui.account.FavoritesSheet
import com.pitstophub.app.ui.account.LocalAccount
import com.pitstophub.app.ui.account.ProfileSheet
import com.pitstophub.app.ui.account.SearchSheet
import com.pitstophub.app.ui.calendar.CalendarScreen
import com.pitstophub.app.ui.categories.CategoriesScreen
import com.pitstophub.app.ui.categories.CategoryHubScreen
import com.pitstophub.app.ui.categories.CategoryHubViewModel
import com.pitstophub.app.ui.categories.HubTarget
import com.pitstophub.app.ui.champions.ChampionsScreen
import com.pitstophub.app.ui.components.PitBottomBar
import com.pitstophub.app.ui.components.PitTopBar
import com.pitstophub.app.ui.components.TopLevelTab
import com.pitstophub.app.ui.drivers.DriverDetailScreen
import com.pitstophub.app.ui.drivers.DriverDetailViewModel
import com.pitstophub.app.ui.grid.GridScreen
import com.pitstophub.app.ui.home.HomeScreen
import com.pitstophub.app.ui.race.RaceDetailScreen
import com.pitstophub.app.ui.race.RaceDetailViewModel
import com.pitstophub.app.ui.standings.StandingsScreen
import com.pitstophub.app.ui.standings.StandingsViewModel
import com.pitstophub.app.ui.teams.TeamDetailScreen
import com.pitstophub.app.ui.teams.TeamDetailViewModel
import com.pitstophub.app.ui.theme.PitColors
import com.pitstophub.app.ui.tributes.LocalTributes
import com.pitstophub.app.ui.tributes.TributeOverlay
import com.pitstophub.app.ui.tributes.rememberTributesState
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/** Folhas (modais) que abrem por cima de qualquer tela. */
private enum class Sheet { AUTH, PROFILE, ALERTS, FAVORITES, SEARCH }

@Composable
fun PitStopHubRoot(container: AppContainer, appViewModel: AppViewModel) {
    val navController = rememberNavController()
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val snackbar = remember { SnackbarHostState() }
    var sheet by rememberSaveable { mutableStateOf<Sheet?>(null) }

    val account = remember(container) { AccountController(container, container.appScope) }
    val tributes = rememberTributesState(scope)

    val categories by appViewModel.categories.collectAsStateWithLifecycle()
    val selectedId by appViewModel.selectedId.collectAsStateWithLifecycle()
    val categoryState by appViewModel.state.collectAsStateWithLifecycle()
    val session by account.session.collectAsStateWithLifecycle()
    val settings by account.settings.collectAsStateWithLifecycle()
    val settingsReady by account.ready.collectAsStateWithLifecycle()
    val alertsEnabled by account.alertsEnabled.collectAsStateWithLifecycle()
    val deepLink by appViewModel.deepLink.collectAsStateWithLifecycle()

    val destination = navController.currentBackStackEntryAsState().value?.destination
    val currentTab: TopLevelTab? = when {
        destination == null || destination.hasRoute<HomeRoute>() -> TopLevelTab.Home
        destination.hasRoute<CalendarRoute>() -> TopLevelTab.Calendar
        destination.hasRoute<StandingsRoute>() -> TopLevelTab.Standings
        destination.hasRoute<GridRoute>() -> TopLevelTab.Grid
        else -> null // telas de detalhe tem a propria barra
    }

    fun openTab(tab: TopLevelTab) {
        val route: Any = when (tab) {
            TopLevelTab.Home -> HomeRoute
            TopLevelTab.Calendar -> CalendarRoute
            TopLevelTab.Standings -> StandingsRoute
            TopLevelTab.Grid -> GridRoute
        }
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Avisos curtos (seguiu / deixou de seguir) e pedido de login vindos de qualquer tela.
    LaunchedEffect(account) {
        account.events.collect { event ->
            when (event) {
                is AccountEvent.Followed -> snackbar.showSnackbar(resources.getString(R.string.toast_followed, event.name))
                is AccountEvent.Unfollowed -> snackbar.showSnackbar(resources.getString(R.string.toast_unfollowed, event.name))
                AccountEvent.NeedsLogin -> {
                    sheet = Sheet.AUTH
                    snackbar.showSnackbar(resources.getString(R.string.toast_login_needed))
                }
            }
        }
    }

    // Toque numa notificacao: abre a categoria e, se houver, a corrida do aviso.
    LaunchedEffect(deepLink) {
        val link = deepLink ?: return@LaunchedEffect
        appViewModel.select(link.categoryId)
        sheet = null
        if (link.raceId != null) navController.navigate(RaceRoute(link.categoryId, link.raceId)) else openTab(TopLevelTab.Calendar)
        appViewModel.consumeDeepLink()
    }

    // Bolinha do sino: ha corrida de algo que a pessoa acompanha nas proximas 48 horas.
    val alertsDot by produceState(false, settings.interestingCategoryIds(), session?.userId, alertsEnabled) {
        val ids = settings.interestingCategoryIds()
        if (session == null || ids.isEmpty() || !alertsEnabled) {
            value = false
            return@produceState
        }
        val limit = Instant.now().plus(Duration.ofHours(48))
        value = ids.any { id ->
            val category = runCatching { container.categories.load(id).category }.getOrNull() ?: return@any false
            category.calendar.any { race ->
                race.status == RaceStatus.UPCOMING && RaceTime.startInstant(race).let { it.isBefore(limit) && it.isAfter(Instant.now().minus(Duration.ofHours(3))) }
            }
        }
    }

    CompositionLocalProvider(
        LocalAppContainer provides container,
        LocalAccount provides account,
        LocalTributes provides tributes,
    ) {
        Box(Modifier.fillMaxSize()) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = currentTab != null,
                drawerContent = {
                    CategoryDrawer(
                        categories = categories,
                        selectedId = selectedId,
                        onSelect = { id ->
                            appViewModel.select(id)
                            scope.launch { drawerState.close() }
                        },
                        onExplore = {
                            scope.launch { drawerState.close() }
                            navController.navigate(CategoriesRoute)
                        },
                        onOpenHub = { id ->
                            scope.launch { drawerState.close() }
                            navController.navigate(CategoryRoute(id))
                        },
                    )
                },
            ) {
                Scaffold(
                    containerColor = PitColors.Carbon,
                    snackbarHost = { SnackbarHost(snackbar) },
                    topBar = {
                        if (currentTab != null) {
                            PitTopBar(
                                onMenu = { scope.launch { drawerState.open() } },
                                onSearch = { sheet = Sheet.SEARCH },
                                onAlerts = { sheet = Sheet.ALERTS },
                                onProfile = { sheet = Sheet.PROFILE },
                                profileInitials = session?.name?.trim()?.take(2)?.uppercase(),
                                alertsDot = alertsDot,
                                onBrandTap = tributes::onLogoTap,
                            )
                        }
                    },
                    bottomBar = {
                        if (currentTab != null) PitBottomBar(selected = currentTab, onSelect = ::openTab)
                    },
                ) { padding ->
                    NavHost(
                        navController = navController,
                        startDestination = HomeRoute,
                        modifier = Modifier.padding(padding),
                        enterTransition = { fadeIn(tween(180)) },
                        exitTransition = { fadeOut(tween(120)) },
                        popEnterTransition = { fadeIn(tween(180)) },
                        popExitTransition = { fadeOut(tween(120)) },
                    ) {
                        composable<HomeRoute> {
                            HomeScreen(
                                state = categoryState,
                                categories = categories,
                                selectedId = selectedId,
                                onSelectCategory = appViewModel::select,
                                onOpenRace = { navController.navigate(RaceRoute(selectedId, it.id)) },
                                onOpenDriver = { navController.navigate(DriverRoute(selectedId, it)) },
                                onOpenTeam = { navController.navigate(TeamRoute(selectedId, it)) },
                                onOpenCategory = { navController.navigate(CategoryRoute(it)) },
                                onOpenStandings = { openTab(TopLevelTab.Standings) },
                                onOpenRules = { navController.navigate(RulesRoute(selectedId)) },
                                onRefresh = appViewModel::refresh,
                            )
                        }
                        composable<CalendarRoute> {
                            CalendarScreen(
                                state = categoryState,
                                onOpenRace = { navController.navigate(RaceRoute(selectedId, it.id)) },
                                onRefresh = appViewModel::refresh,
                            )
                        }
                        composable<StandingsRoute> {
                            val viewModel: StandingsViewModel = viewModel(factory = viewModelFactory { initializer { StandingsViewModel(container.categories) } })
                            val movement by viewModel.movement.collectAsStateWithLifecycle()
                            LaunchedEffect(categoryState.data?.category?.id, categoryState.data?.season, categoryState.data?.liveStandings) {
                                categoryState.data?.let { viewModel.load(it) }
                            }
                            StandingsScreen(
                                state = categoryState,
                                movement = movement,
                                onOpenDriver = { navController.navigate(DriverRoute(selectedId, it)) },
                                onOpenTeam = { navController.navigate(TeamRoute(selectedId, it)) },
                                onOpenRules = { navController.navigate(RulesRoute(selectedId, it)) },
                                onRefresh = appViewModel::refresh,
                            )
                        }
                        composable<GridRoute> {
                            GridScreen(
                                state = categoryState,
                                onOpenDriver = { navController.navigate(DriverRoute(selectedId, it.id)) },
                                onOpenTeam = { navController.navigate(TeamRoute(selectedId, it.id)) },
                                onRefresh = appViewModel::refresh,
                            )
                        }
                        composable<RaceRoute> { entry ->
                            val route = entry.toRoute<RaceRoute>()
                            val viewModel: RaceDetailViewModel = viewModel(
                                factory = viewModelFactory { initializer { RaceDetailViewModel(container, route.categoryId, route.raceId) } },
                            )
                            val state by viewModel.state.collectAsStateWithLifecycle()
                            RaceDetailScreen(
                                state = state,
                                onBack = { navController.popBackStack() },
                                onOpenDriver = { navController.navigate(DriverRoute(route.categoryId, it)) },
                            )
                        }
                        composable<TeamRoute> { entry ->
                            val route = entry.toRoute<TeamRoute>()
                            val viewModel: TeamDetailViewModel = viewModel(
                                factory = viewModelFactory { initializer { TeamDetailViewModel(container, route.categoryId, route.teamId) } },
                            )
                            val state by viewModel.state.collectAsStateWithLifecycle()
                            TeamDetailScreen(
                                state = state,
                                onBack = { navController.popBackStack() },
                                onOpenDriver = { navController.navigate(DriverRoute(route.categoryId, it)) },
                                onOpenStandings = {
                                    appViewModel.select(route.categoryId)
                                    openTab(TopLevelTab.Standings)
                                },
                            )
                        }
                        composable<DriverRoute> { entry ->
                            val route = entry.toRoute<DriverRoute>()
                            val viewModel: DriverDetailViewModel = viewModel(
                                factory = viewModelFactory { initializer { DriverDetailViewModel(container, route.categoryId, route.driverId) } },
                            )
                            val state by viewModel.state.collectAsStateWithLifecycle()
                            DriverDetailScreen(
                                state = state,
                                onBack = { navController.popBackStack() },
                                onOpenTeam = { navController.navigate(TeamRoute(route.categoryId, it)) },
                                onOpenRace = { navController.navigate(RaceRoute(route.categoryId, it)) },
                                onOpenChampions = { navController.navigate(ChampionsRoute) },
                            )
                        }
                        composable<CategoriesRoute> {
                            CategoriesScreen(
                                categories = categories,
                                onBack = { navController.popBackStack() },
                                onOpenCategory = { navController.navigate(CategoryRoute(it)) },
                            )
                        }
                        composable<CategoryRoute> { entry ->
                            val route = entry.toRoute<CategoryRoute>()
                            val viewModel: CategoryHubViewModel = viewModel(
                                factory = viewModelFactory { initializer { CategoryHubViewModel(container.categories, route.categoryId) } },
                            )
                            val state by viewModel.state.collectAsStateWithLifecycle()
                            CategoryHubScreen(
                                state = state,
                                onBack = { navController.popBackStack() },
                                onUseCategory = { target ->
                                    appViewModel.select(route.categoryId)
                                    openTab(if (target == HubTarget.CALENDAR) TopLevelTab.Calendar else TopLevelTab.Standings)
                                },
                                onOpenRace = { navController.navigate(RaceRoute(route.categoryId, it.id)) },
                                onOpenTeam = { navController.navigate(TeamRoute(route.categoryId, it)) },
                                onOpenDriver = { navController.navigate(DriverRoute(route.categoryId, it)) },
                                onOpenChampions = { navController.navigate(ChampionsRoute) },
                                onOpenRules = { navController.navigate(RulesRoute(route.categoryId)) },
                            )
                        }
                        composable<RulesRoute> { entry ->
                            val route = entry.toRoute<RulesRoute>()
                            val viewModel: CategoryHubViewModel = viewModel(
                                factory = viewModelFactory { initializer { CategoryHubViewModel(container.categories, route.categoryId) } },
                            )
                            val state by viewModel.state.collectAsStateWithLifecycle()
                            RulesScreen(state = state, initialSection = route.section, onBack = { navController.popBackStack() })
                        }
                        composable<ChampionsRoute> {
                            ChampionsScreen(
                                champions = container.webData.champions,
                                onBack = { navController.popBackStack() },
                                onOpen = { navController.navigate(DriverRoute("f1", it.id)) },
                            )
                        }
                    }
                }
            }

            // folhas
            when (sheet) {
                Sheet.AUTH -> AuthSheet(onDismiss = { sheet = null })
                Sheet.PROFILE -> ProfileSheet(
                    onDismiss = { sheet = null },
                    onLogin = { sheet = Sheet.AUTH },
                    onOpenFavorites = { sheet = Sheet.FAVORITES },
                    onOpenAlerts = { sheet = Sheet.ALERTS },
                    onOpenChampions = {
                        sheet = null
                        navController.navigate(ChampionsRoute)
                    },
                    onOpenCategories = {
                        sheet = null
                        navController.navigate(CategoriesRoute)
                    },
                )
                Sheet.ALERTS -> AlertsSheet(
                    onDismiss = { sheet = null },
                    onLogin = { sheet = Sheet.AUTH },
                    onOpenRace = { categoryId, raceId ->
                        sheet = null
                        navController.navigate(RaceRoute(categoryId, raceId))
                    },
                )
                Sheet.FAVORITES -> FavoritesSheet(onDismiss = { sheet = null })
                Sheet.SEARCH -> SearchSheet(
                    onDismiss = { sheet = null },
                    onOpenCategory = { navController.navigate(CategoryRoute(it)) },
                    onOpenDriver = { categoryId, id -> navController.navigate(DriverRoute(categoryId, id)) },
                    onOpenTeam = { categoryId, id -> navController.navigate(TeamRoute(categoryId, id)) },
                    onOpenRace = { categoryId, id -> navController.navigate(RaceRoute(categoryId, id)) },
                )
                null -> if (session != null && settingsReady && !settings.favoritesOnboarded) {
                    FavoritesSheet(onDismiss = {}, onboarding = true)
                }
            }

            TributeOverlay(tributes)
        }
    }
}
