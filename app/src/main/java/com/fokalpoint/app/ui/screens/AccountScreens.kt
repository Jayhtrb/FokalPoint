package com.fokalpoint.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fokalpoint.app.BuildConfig
import com.fokalpoint.app.ui.components.*
import com.fokalpoint.app.ui.navigation.NavBarClearance
import com.fokalpoint.app.ui.theme.Aurora
import com.fokalpoint.app.ui.utils.LegalDocs
import com.fokalpoint.app.ui.viewmodel.FokalViewModel
import com.fokalpoint.app.ui.viewmodel.ProViewModel

// ============================================================================ PROFILE / SETTINGS

@Composable
fun ProfileScreen(
    viewModel: FokalViewModel,
    onSaved: () -> Unit,
    onPro: () -> Unit,
    onEditProfile: () -> Unit,
    onLegal: (String) -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    val profile by viewModel.currentUserProfile.collectAsStateWithLifecycle()
    val role by viewModel.currentUserRole.collectAsStateWithLifecycle()
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmCreator by remember { mutableStateOf(false) }
    val isCreator = role == "Creator"

    LazyColumn(Modifier.fillMaxSize().testTag("profile_screen"), contentPadding = PaddingValues(bottom = NavBarClearance)) {
        item {
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(profile?.profileImage.orEmpty(), profile?.name.orEmpty(), 96.dp, proRing = isPro)
                Spacer(Modifier.height(14.dp))
                Text(profile?.name.orEmpty().ifBlank { "Your account" }, style = MaterialTheme.typography.headlineMedium, color = Aurora.TextPrimary)
                Text(profile?.email.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = Aurora.TextSecondary)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(if (isCreator) "Creator" else "Client", Aurora.Cyan)
                    if (isPro) ProBadge()
                    if (viewModel.isDemoMode) Pill("Demo mode", Aurora.Amber)
                }
            }
        }
        item {
            Spacer(Modifier.height(24.dp))
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isCreator) {
                    SettingsRow(Icons.Rounded.WorkspacePremium, if (isPro) "Creator Pro" else "Upgrade to Creator Pro",
                        if (isPro) "Manage your subscription" else "Featured placement & unlimited leads", onPro, accent = true)
                    SettingsRow(Icons.Rounded.Edit, "Profile & pricing", "Headline, rates and UPI", onEditProfile)
                } else {
                    SettingsRow(Icons.Rounded.FavoriteBorder, "Saved creators", "Your shortlist", onSaved)
                    SettingsRow(Icons.Rounded.CameraAlt, "Become a creator", "Get booked for your photography", { confirmCreator = true })
                }
                SettingsRow(Icons.Rounded.Notifications, "Notifications", "Booking & message alerts", {
                    val ctx = viewModel.getApplication<android.app.Application>()
                    val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { ctx.startActivity(intent) }
                })
                SettingsRow(Icons.Rounded.HelpOutline, "Help & support", "support@fokalpoint.app", { uri.openUri("mailto:support@fokalpoint.app") })
                SettingsRow(Icons.Rounded.Policy, "Privacy policy", null, { onLegal(LegalDocs.PRIVACY) })
                SettingsRow(Icons.Rounded.Gavel, "Terms of service", null, { onLegal(LegalDocs.TERMS) })
                Spacer(Modifier.height(6.dp))
                SettingsRow(Icons.AutoMirrored.Rounded.Logout, "Sign out", null, onSignOut, testTag = "sign_out")
                SettingsRow(Icons.Rounded.DeleteForever, "Delete account", "Permanently remove your data", { confirmDelete = true },
                    tint = Aurora.Coral, testTag = "delete_account")
                Spacer(Modifier.height(10.dp))
                Text("FokalPoint ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = Aurora.TextTertiary,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Aurora.Elevated,
            title = { Text("Delete your account?") },
            text = { Text("This permanently deletes your profile, bookings, messages, reviews and photos. This can't be undone.") },
            confirmButton = { TextButton({ confirmDelete = false; onDeleteAccount() }, Modifier.testTag("confirm_delete")) { Text("Delete", color = Aurora.Coral) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Keep account") } }
        )
    }
    if (confirmCreator) {
        AlertDialog(
            onDismissRequest = { confirmCreator = false },
            containerColor = Aurora.Elevated,
            title = { Text("Switch to a creator account?") },
            text = { Text("You'll get a Studio to manage bookings, your portfolio and leads. Your existing bookings stay in your history.") },
            confirmButton = { TextButton({ confirmCreator = false; viewModel.switchRole("Creator") }) { Text("Switch") } },
            dismissButton = { TextButton({ confirmCreator = false }) { Text("Not now") } }
        )
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit,
    accent: Boolean = false, tint: Color = Aurora.TextPrimary, testTag: String = ""
) {
    GlassCard(Modifier.fillMaxWidth().then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
        highlight = accent, onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(13.dp))
                    .background(if (accent) Aurora.ProBrush else Brush.linearGradient(listOf(Aurora.GlassStrong, Aurora.GlassStrong))),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = if (accent) Aurora.Void else tint, modifier = Modifier.size(21.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = tint)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Aurora.TextSecondary)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = Aurora.TextTertiary)
        }
    }
}

// ============================================================================ CREATOR PRO PAYWALL

private val ProPerks = listOf(
    Triple(Icons.Rounded.TrendingUp, "Featured placement", "Appear first in search and on the Discover screen."),
    Triple(Icons.Rounded.Bolt, "Unlimited shoot leads", "See every client request, not just the newest 3."),
    Triple(Icons.Rounded.PhotoLibrary, "Unlimited portfolio", "Show your full body of work (free: 12 photos)."),
    Triple(Icons.Rounded.WorkspacePremium, "Pro badge & glow ring", "Stand out with a premium profile clients trust."),
    Triple(Icons.Rounded.MoneyOff, "0% commission", "Clients pay you directly. Always.")
)

@Composable
fun ProScreen(viewModel: FokalViewModel, onBack: () -> Unit, onLegal: (String) -> Unit) {
    val pro: ProViewModel = viewModel()
    val plans by pro.plans.collectAsStateWithLifecycle()
    val available by pro.billingAvailable.collectAsStateWithLifecycle()
    val state by pro.state.collectAsStateWithLifecycle()
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val creator by viewModel.currentCreatorDetails.collectAsStateWithLifecycle()
    val activity = androidx.activity.compose.LocalActivity.current
    val uri = LocalUriHandler.current
    var selected by remember(plans) { mutableStateOf(plans.firstOrNull { it.isYearly } ?: plans.firstOrNull()) }
    LaunchedEffect(pro) { pro.onVerified = { viewModel.onProVerified(it) } }

    Box(Modifier.fillMaxSize().testTag("pro_screen")) {
        Box(Modifier.fillMaxWidth().height(360.dp).background(Brush.verticalGradient(listOf(Color(0xFF2B1557), Color(0xFF0D2A35), Color.Transparent))))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Row(Modifier.statusBarsPadding().padding(16.dp)) { CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack) }
            Column(Modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(84.dp).clip(RoundedCornerShape(28.dp)).background(Aurora.ProBrush), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.WorkspacePremium, null, tint = Aurora.Void, modifier = Modifier.size(44.dp))
                }
                Spacer(Modifier.height(18.dp))
                GradientText("Creator Pro", MaterialTheme.typography.displaySmall, brush = Aurora.ProBrush)
                Spacer(Modifier.height(6.dp))
                Text("Get discovered first and win more bookings.", style = MaterialTheme.typography.bodyLarge,
                    color = Aurora.TextSecondary, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(26.dp))
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ProPerks.forEach { (icon, title, body) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(CircleShape).background(Aurora.Glass).border(1.dp, Aurora.ProBrush, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(icon, null, tint = Aurora.Amber, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(title, style = MaterialTheme.typography.titleSmall, color = Aurora.TextPrimary)
                            Text(body, style = MaterialTheme.typography.bodySmall, color = Aurora.TextSecondary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(26.dp))

            Column(Modifier.padding(horizontal = 20.dp)) {
                when {
                    isPro -> {
                        GlassCard(Modifier.fillMaxWidth(), highlight = true) {
                            Text("You're Pro ✦", style = MaterialTheme.typography.titleLarge, color = Aurora.TextPrimary)
                            val until = creator?.proUntil ?: 0
                            Text("Renews or ends on " + java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(until)),
                                style = MaterialTheme.typography.bodyMedium, color = Aurora.TextSecondary)
                            Spacer(Modifier.height(14.dp))
                            GlassButton("Manage in Google Play", {
                                uri.openUri("https://play.google.com/store/account/subscriptions?sku=creator_pro&package=${BuildConfig.APPLICATION_ID}")
                            }, Modifier.fillMaxWidth(), icon = Icons.Rounded.OpenInNew)
                        }
                    }
                    plans.isNotEmpty() -> {
                        plans.forEach { plan ->
                            val isSel = plan == selected
                            GlassCard(Modifier.fillMaxWidth().padding(vertical = 5.dp).testTag("plan_${plan.basePlanId}"), highlight = isSel, onClick = { selected = plan }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(if (plan.isYearly) "Yearly" else "Monthly", style = MaterialTheme.typography.titleMedium, color = Aurora.TextPrimary)
                                            if (plan.isYearly) { Spacer(Modifier.width(8.dp)); Pill("Best value", Aurora.Mint) }
                                        }
                                        plan.freeTrialPeriod?.let { Text("Free trial included", style = MaterialTheme.typography.bodySmall, color = Aurora.Mint) }
                                    }
                                    Text("${plan.formattedPrice}/${if (plan.isYearly) "yr" else "mo"}", style = MaterialTheme.typography.titleMedium, color = Aurora.TextPrimary)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        GradientButton("Continue", { selected?.let { p -> activity?.let { pro.purchase(it, p) } } },
                            Modifier.fillMaxWidth().testTag("pro_buy"), brush = Aurora.ProBrush, loading = state is ProViewModel.State.Working,
                            enabled = selected != null)
                    }
                    viewModel.isDemoMode -> {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Text("₹499/month · ₹3,999/year", style = MaterialTheme.typography.titleLarge, color = Aurora.TextPrimary)
                            Text("Suggested pricing. Real prices come from Google Play once the app is published.",
                                style = MaterialTheme.typography.bodySmall, color = Aurora.TextSecondary)
                        }
                        Spacer(Modifier.height(12.dp))
                        GradientButton("Preview Pro (demo)", { viewModel.previewProInDemo() }, Modifier.fillMaxWidth().testTag("pro_preview"),
                            brush = Aurora.ProBrush, icon = Icons.Rounded.AutoAwesome)
                    }
                    else -> {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Text(if (available == false) "Google Play isn't available" else "Loading plans…",
                                style = MaterialTheme.typography.titleMedium, color = Aurora.TextPrimary)
                            Text("Subscriptions work when FokalPoint is installed from Google Play.",
                                style = MaterialTheme.typography.bodySmall, color = Aurora.TextSecondary)
                        }
                    }
                }
                (state as? ProViewModel.State.Error)?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it.message, style = MaterialTheme.typography.bodySmall, color = Aurora.Coral)
                }
                if (!isPro && !viewModel.isDemoMode) {
                    Text("Restore purchase", style = MaterialTheme.typography.labelLarge, color = Aurora.VioletSoft,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 14.dp).clip(CircleShape).clickable { pro.restore() }.padding(8.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text("Auto-renews until cancelled. Cancel anytime in Google Play. By subscribing you agree to the Terms.",
                    style = MaterialTheme.typography.bodySmall, color = Aurora.TextTertiary, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable { onLegal(LegalDocs.TERMS) })
            }
        }
    }
}

// ============================================================================ LEGAL

@Composable
fun LegalScreen(doc: String, onBack: () -> Unit) {
    val (title, body) = LegalDocs.get(doc)
    Column(Modifier.fillMaxSize()) {
        TopBar(title, onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 40.dp)) {
            body.split("\n\n").forEach { para ->
                val heading = para.startsWith("## ")
                Text(
                    para.removePrefix("## "),
                    style = if (heading) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                    color = if (heading) Aurora.TextPrimary else Aurora.TextSecondary,
                    modifier = Modifier.padding(top = if (heading) 18.dp else 8.dp)
                )
            }
        }
    }
}
