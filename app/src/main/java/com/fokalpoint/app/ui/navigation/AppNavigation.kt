package com.fokalpoint.app.ui.navigation

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fokalpoint.app.ui.components.AuroraBackground
import com.fokalpoint.app.ui.screens.*
import com.fokalpoint.app.ui.theme.Aurora
import com.fokalpoint.app.ui.viewmodel.FokalViewModel

object Routes {
    const val HOME = "home"
    const val EXPLORE = "explore"
    const val BOOKINGS = "bookings"
    const val INBOX = "inbox"
    const val PROFILE = "profile"
    const val STUDIO = "studio"
    const val LEADS = "leads"
    const val CREATOR = "creator/{id}"
    const val BOOK = "book/{id}"
    const val CHAT = "chat/{id}"
    const val PRO = "pro"
    const val EDIT_PROFILE = "edit_profile"
    const val PORTFOLIO = "portfolio"
    const val CALENDAR = "calendar"
    const val SAVED = "saved"
    const val POST_REQUEST = "post_request"
    const val LEGAL = "legal/{doc}"

    fun creator(id: String) = "creator/$id"
    fun book(id: String) = "book/$id"
    fun chat(id: String) = "chat/$id"
    fun legal(doc: String) = "legal/$doc"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val customerTabs = listOf(
    Tab(Routes.HOME, "Discover", Icons.Rounded.AutoAwesome),
    Tab(Routes.EXPLORE, "Explore", Icons.Rounded.Search),
    Tab(Routes.BOOKINGS, "Bookings", Icons.Rounded.EventAvailable),
    Tab(Routes.INBOX, "Inbox", Icons.Rounded.ChatBubble),
    Tab(Routes.PROFILE, "You", Icons.Rounded.Person)
)

private val creatorTabs = listOf(
    Tab(Routes.STUDIO, "Studio", Icons.Rounded.Dashboard),
    Tab(Routes.BOOKINGS, "Requests", Icons.Rounded.EventAvailable),
    Tab(Routes.LEADS, "Leads", Icons.Rounded.Bolt),
    Tab(Routes.INBOX, "Inbox", Icons.Rounded.ChatBubble),
    Tab(Routes.PROFILE, "You", Icons.Rounded.Person)
)

/** Root of the signed-in app. */
@Composable
fun FokalApp(viewModel: FokalViewModel, onSignOut: () -> Unit, onDeleteAccount: () -> Unit) {
    val role by viewModel.currentUserRole.collectAsStateWithLifecycle()
    val isCreator = role == "Creator"
    val tabs = if (isCreator) creatorTabs else customerTabs
    val start = if (isCreator) Routes.STUDIO else Routes.HOME
    val nav = rememberNavController()
    val context = LocalContext.current

    val toast by viewModel.toast.collectAsStateWithLifecycle()
    LaunchedEffect(toast) {
        toast?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); viewModel.toast.value = null }
    }

    // Switching role (e.g. "Become a creator") lands on that role's home.
    var lastRole by remember { mutableStateOf(role) }
    LaunchedEffect(role) {
        if (role != lastRole) {
            lastRole = role
            nav.navigate(start) { popUpTo(nav.graph.findStartDestination().id) { inclusive = true } }
        }
    }

    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val showBar = tabs.any { it.route == current }

    AuroraBackground {
        NavHost(
            navController = nav,
            startDestination = start,
            enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 12 } },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(260)) { it / 12 } },
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Routes.HOME) {
                HomeScreen(viewModel,
                    onCreator = { nav.navigate(Routes.creator(it)) },
                    onExplore = { category -> viewModel.selectedCategory.value = category; nav.navigateTab(Routes.EXPLORE) },
                    onPostRequest = { nav.navigate(Routes.POST_REQUEST) },
                    onSaved = { nav.navigate(Routes.SAVED) })
            }
            composable(Routes.EXPLORE) { ExploreScreen(viewModel, onCreator = { nav.navigate(Routes.creator(it)) }) }
            composable(Routes.BOOKINGS) {
                BookingsScreen(viewModel,
                    onChat = { nav.navigate(Routes.chat(it)) },
                    onCreator = { nav.navigate(Routes.creator(it)) },
                    onExplore = { nav.navigateTab(if (isCreator) Routes.LEADS else Routes.EXPLORE) })
            }
            composable(Routes.INBOX) { InboxScreen(viewModel, onChat = { nav.navigate(Routes.chat(it)) }) }
            composable(Routes.PROFILE) {
                ProfileScreen(viewModel,
                    onSaved = { nav.navigate(Routes.SAVED) },
                    onPro = { nav.navigate(Routes.PRO) },
                    onEditProfile = { nav.navigate(Routes.EDIT_PROFILE) },
                    onLegal = { nav.navigate(Routes.legal(it)) },
                    onSignOut = onSignOut,
                    onDeleteAccount = onDeleteAccount)
            }
            composable(Routes.STUDIO) {
                StudioScreen(viewModel,
                    onPro = { nav.navigate(Routes.PRO) },
                    onRequests = { nav.navigateTab(Routes.BOOKINGS) },
                    onPortfolio = { nav.navigate(Routes.PORTFOLIO) },
                    onCalendar = { nav.navigate(Routes.CALENDAR) },
                    onEditProfile = { nav.navigate(Routes.EDIT_PROFILE) },
                    onLeads = { nav.navigateTab(Routes.LEADS) },
                    onPreview = { id -> nav.navigate(Routes.creator(id)) })
            }
            composable(Routes.LEADS) { LeadsScreen(viewModel, onPro = { nav.navigate(Routes.PRO) }, onChat = { nav.navigate(Routes.chat(it)) }) }
            composable(Routes.CREATOR, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                CreatorProfileScreen(id, viewModel,
                    onBack = { nav.popBackStack() },
                    onBook = { nav.navigate(Routes.book(id)) },
                    onChat = { nav.navigate(Routes.chat(id)) })
            }
            composable(Routes.BOOK, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                BookingRequestScreen(id, viewModel,
                    onBack = { nav.popBackStack() },
                    onDone = { nav.navigate(Routes.BOOKINGS) { popUpTo(nav.graph.findStartDestination().id); launchSingleTop = true } })
            }
            composable(Routes.CHAT, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                ChatScreen(entry.arguments?.getString("id").orEmpty(), viewModel, onBack = { nav.popBackStack() },
                    onProfile = { nav.navigate(Routes.creator(it)) })
            }
            composable(Routes.PRO) { ProScreen(viewModel, onBack = { nav.popBackStack() }, onLegal = { nav.navigate(Routes.legal(it)) }) }
            composable(Routes.EDIT_PROFILE) { EditProfileScreen(viewModel, onBack = { nav.popBackStack() }) }
            composable(Routes.PORTFOLIO) { PortfolioManagerScreen(viewModel, onBack = { nav.popBackStack() }, onPro = { nav.navigate(Routes.PRO) }) }
            composable(Routes.CALENDAR) { AvailabilityScreen(viewModel, onBack = { nav.popBackStack() }) }
            composable(Routes.SAVED) { SavedScreen(viewModel, onBack = { nav.popBackStack() }, onCreator = { nav.navigate(Routes.creator(it)) }) }
            composable(Routes.POST_REQUEST) { PostRequestScreen(viewModel, onBack = { nav.popBackStack() }) }
            composable(Routes.LEGAL, arguments = listOf(navArgument("doc") { type = NavType.StringType })) { entry ->
                LegalScreen(entry.arguments?.getString("doc").orEmpty(), onBack = { nav.popBackStack() })
            }
        }

        AnimatedVisibility(
            visible = showBar,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            FloatingNavBar(tabs, current) { nav.navigateTab(it) }
        }
    }
}

private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun FloatingNavBar(tabs: List<Tab>, current: String?, onSelect: (String) -> Unit) {
    val shape = RoundedCornerShape(32.dp)
    Row(
        Modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .shadow(24.dp, shape, ambientColor = Aurora.Violet, spotColor = Aurora.Violet)
            .clip(shape)
            .background(Color(0xE6101018))
            .border(1.dp, Aurora.GlassBorder, shape)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .testTag("bottom_nav"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            val selected = tab.route == current
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(tab.route) }
                    .padding(vertical = 6.dp)
                    .testTag("tab_${tab.route}"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .size(width = 48.dp, height = 30.dp)
                        .clip(CircleShape)
                        .then(if (selected) Modifier.background(Aurora.Signature) else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(tab.icon, tab.label, tint = if (selected) Color.White else Aurora.TextTertiary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(tab.label, style = MaterialTheme.typography.labelSmall, color = if (selected) Aurora.TextPrimary else Aurora.TextTertiary)
            }
        }
    }
}

/** Space the floating nav bar occupies, for screen content padding. */
val NavBarClearance = 112.dp

@Suppress("unused")
private val noTransition: EnterTransition = EnterTransition.None
@Suppress("unused")
private val noExit: ExitTransition = ExitTransition.None
