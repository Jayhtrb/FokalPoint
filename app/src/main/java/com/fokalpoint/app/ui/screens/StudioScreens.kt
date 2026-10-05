package com.fokalpoint.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fokalpoint.app.ui.components.*
import com.fokalpoint.app.ui.navigation.NavBarClearance
import com.fokalpoint.app.ui.theme.Aurora
import com.fokalpoint.app.ui.utils.BookingDates
import com.fokalpoint.app.ui.viewmodel.FokalViewModel

// ============================================================================ STUDIO (creator home)

@Composable
fun StudioScreen(
    viewModel: FokalViewModel, onPro: () -> Unit, onRequests: () -> Unit, onPortfolio: () -> Unit,
    onCalendar: () -> Unit, onEditProfile: () -> Unit, onLeads: () -> Unit, onPreview: (String) -> Unit
) {
    val profile by viewModel.currentUserProfile.collectAsStateWithLifecycle()
    val me by viewModel.currentCreatorDetails.collectAsStateWithLifecycle()
    val stats by viewModel.studioStats.collectAsStateWithLifecycle()
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val portfolio by viewModel.myPortfolio.collectAsStateWithLifecycle()
    val name = profile?.name.orEmpty()

    LazyColumn(Modifier.fillMaxSize().testTag("studio_screen"), contentPadding = PaddingValues(bottom = NavBarClearance)) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 16.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("YOUR STUDIO", style = MaterialTheme.typography.labelSmall, color = Aurora.TextTertiary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name.substringBefore(' ').ifBlank { "Creator" }, style = MaterialTheme.typography.headlineMedium, color = Aurora.TextPrimary)
                        if (isPro) { Spacer(Modifier.width(8.dp)); ProBadge() }
                    }
                }
                Avatar(profile?.profileImage.orEmpty(), name, 46.dp, proRing = isPro)
            }
        }
        item {
            GlassCard(Modifier.padding(20.dp).fillMaxWidth(), highlight = true, contentPadding = PaddingValues(20.dp)) {
                Text("Earned on FokalPoint", style = MaterialTheme.typography.labelMedium, color = Aurora.TextSecondary)
                GradientText(formatInr(stats.earned), MaterialTheme.typography.displaySmall)
                Text("${formatInr(stats.upcoming)} in upcoming shoots", style = MaterialTheme.typography.bodySmall, color = Aurora.TextTertiary)
                Spacer(Modifier.height(18.dp))
                EarningsBars(stats.monthly, Modifier.fillMaxWidth().height(110.dp))
            }
        }
        item {
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("${stats.pendingRequests}", "new requests", Modifier.weight(1f).clickable(onClick = onRequests), Aurora.Amber)
                StatTile("${stats.completed}", "completed", Modifier.weight(1f), Aurora.Mint)
                StatTile(if ((me?.reviewCount ?: 0) == 0) "New" else String.format(java.util.Locale.US, "%.1f", me?.rating ?: 0.0), "rating", Modifier.weight(1f), Aurora.Cyan)
            }
        }
        if (!isPro) {
            item {
                Box(
                    Modifier.padding(20.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF2A1450), Color(0xFF0B3440))))
                        .border(1.dp, Aurora.ProBrush, RoundedCornerShape(28.dp))
                        .clickable(onClick = onPro).padding(20.dp).testTag("studio_pro_upsell")
                ) {
                    Column {
                        ProBadge()
                        Spacer(Modifier.height(10.dp))
                        Text("Stand out. Get booked.", style = MaterialTheme.typography.headlineSmall, color = Aurora.TextPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text("Featured placement, unlimited leads and an unlimited portfolio with Creator Pro.",
                            style = MaterialTheme.typography.bodyMedium, color = Aurora.TextSecondary)
                        Spacer(Modifier.height(14.dp))
                        GradientButton("See Creator Pro", onPro, brush = Aurora.ProBrush, height = 46.dp)
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(if (isPro) 20.dp else 0.dp))
            SectionHeader("Manage", Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(10.dp))
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StudioAction(Icons.Rounded.PhotoLibrary, "Portfolio", "${portfolio.size}${if (isPro) "" else "/12"} photos", onPortfolio)
                StudioAction(Icons.Rounded.CalendarMonth, "Availability", "Block dates you can't shoot", onCalendar)
                StudioAction(Icons.Rounded.Bolt, "Shoot leads", if (isPro) "Unlimited with Pro" else "3 free leads · Pro unlocks all", onLeads)
                StudioAction(Icons.Rounded.Edit, "Profile & pricing", "Headline, rates, UPI for payouts", onEditProfile)
                StudioAction(Icons.Rounded.Visibility, "Preview public profile", "See what clients see", { me?.id?.let(onPreview) })
            }
        }
    }
}

@Composable
private fun StudioAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth().testTag("studio_${title.lowercase().replace(' ', '_')}"), onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Aurora.Violet.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Aurora.VioletSoft, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Aurora.TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Aurora.TextSecondary)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = Aurora.TextTertiary)
        }
    }
}

@Composable
private fun EarningsBars(data: List<Pair<String, Double>>, modifier: Modifier) {
    val max = (data.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(1.0)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val n = data.size.coerceAtLeast(1)
            val slot = size.width / n
            val barW = slot * 0.46f
            data.forEachIndexed { i, (_, v) ->
                val h = (size.height * (v / max)).toFloat().coerceAtLeast(6f)
                drawRoundRect(
                    brush = if (i == data.lastIndex) Brush.verticalGradient(listOf(Aurora.Cyan, Aurora.Violet))
                    else Brush.verticalGradient(listOf(Aurora.Violet.copy(alpha = 0.55f), Aurora.Violet.copy(alpha = 0.18f))),
                    topLeft = Offset(i * slot + (slot - barW) / 2, size.height - h),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(barW / 2, barW / 2)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            data.forEach { (label, _) ->
                Text(label, style = MaterialTheme.typography.labelSmall, color = Aurora.TextTertiary, modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

// ============================================================================ AVAILABILITY

@Composable
fun AvailabilityScreen(viewModel: FokalViewModel, onBack: () -> Unit) {
    val me by viewModel.currentUserId.collectAsStateWithLifecycle()
    val blocked by viewModel.blockedDatesState.collectAsStateWithLifecycle()
    val bookings by viewModel.creatorBookings.collectAsStateWithLifecycle()
    val months = remember { BookingDates.upcomingMonths(3) }
    var monthIndex by remember { mutableIntStateOf(0) }
    val month = months[monthIndex]
    val mine = blocked[me].orEmpty().toSet()
    val bookedDates = bookings.filter { it.status == "Accepted" || it.status == "Confirmed" }.map { it.date }.toSet()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar("Availability", onBack)
        Text("Tap a date to block or unblock it. Booked shoots are locked automatically.",
            style = MaterialTheme.typography.bodyMedium, color = Aurora.TextSecondary, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(16.dp))
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            months.forEachIndexed { i, m -> AuroraChip(m.label, i == monthIndex, { monthIndex = i }) }
        }
        Spacer(Modifier.height(16.dp))
        GlassCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
            Row(Modifier.fillMaxWidth()) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = Aurora.TextTertiary, modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            Spacer(Modifier.height(8.dp))
            (List(month.leadingBlanks) { "" } + month.dates).chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { d ->
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                            if (d.isNotEmpty()) {
                                val past = BookingDates.isPast(d)
                                val isBooked = d in bookedDates
                                val isBlocked = d in mine
                                Box(
                                    Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                                        .background(
                                            when {
                                                isBooked -> Brush.linearGradient(listOf(Aurora.Mint.copy(alpha = 0.35f), Aurora.Mint.copy(alpha = 0.2f)))
                                                isBlocked -> Brush.linearGradient(listOf(Aurora.Coral.copy(alpha = 0.3f), Aurora.Coral.copy(alpha = 0.18f)))
                                                else -> Brush.linearGradient(listOf(Aurora.Glass, Aurora.Glass))
                                            }
                                        )
                                        .clickable(enabled = !past && !isBooked) { viewModel.toggleBlockedDate(d) }
                                        .testTag("avail_$d"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(d.takeLast(2).trimStart('0'), style = MaterialTheme.typography.labelLarge,
                                        color = if (past) Aurora.TextTertiary.copy(alpha = 0.4f) else Aurora.TextPrimary)
                                }
                            }
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Blocked", Aurora.Coral); Pill("Booked", Aurora.Mint); Pill("Open", Aurora.TextSecondary)
        }
        Spacer(Modifier.height(32.dp))
    }
}

// ============================================================================ PORTFOLIO MANAGER

@Composable
fun PortfolioManagerScreen(viewModel: FokalViewModel, onBack: () -> Unit, onPro: () -> Unit) {
    val photos by viewModel.myPortfolio.collectAsStateWithLifecycle()
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val uploading by viewModel.uploading.collectAsStateWithLifecycle()
    var category by remember { mutableStateOf("Wedding") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.addPortfolioPhoto(uri, category)
    }
    val full = !isPro && photos.size >= 12

    Column(Modifier.fillMaxSize()) {
        TopBar("Portfolio", onBack) { Pill(if (isPro) "${photos.size} · unlimited" else "${photos.size}/12", if (full) Aurora.Amber else Aurora.Cyan) }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Categories.forEach { AuroraChip(it.name, category == it.name, { category = it.name }) }
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.padding(horizontal = 20.dp)) {
            if (full) {
                GradientButton("Upgrade for unlimited photos", onPro, Modifier.fillMaxWidth(), brush = Aurora.ProBrush, icon = Icons.Rounded.WorkspacePremium)
            } else {
                GradientButton("Add $category photo", { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    Modifier.fillMaxWidth().testTag("add_photo"), icon = Icons.Rounded.AddPhotoAlternate, loading = uploading)
            }
        }
        Spacer(Modifier.height(14.dp))
        if (photos.isEmpty()) {
            EmptyState(Icons.Rounded.PhotoLibrary, "Show your best work", "Clients book with their eyes. Add 6+ photos to stand out in search.")
        }
        LazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(photos, key = { it.id }) { p ->
                Box(Modifier.aspectRatio(0.8f).clip(RoundedCornerShape(14.dp))) {
                    FokalImage(p.mediaUrl, p.title, Modifier.fillMaxSize())
                    CircleIconButton(Icons.Rounded.Close, "Remove", { viewModel.deletePortfolioPhoto(p) },
                        Modifier.align(Alignment.TopEnd).padding(6.dp), size = 28.dp)
                }
            }
        }
    }
}

// ============================================================================ LEADS

@Composable
fun LeadsScreen(viewModel: FokalViewModel, onPro: () -> Unit, onChat: (String) -> Unit) {
    val leads by viewModel.leads.collectAsStateWithLifecycle()
    val total by viewModel.leadsTotal.collectAsStateWithLifecycle()
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshLeadsNow() }
    // Demo data is local; with a backend the server already limits free creators to 3.
    val visible = if (isPro || !viewModel.isDemoMode) leads else leads.take(3)
    val hidden = if (viewModel.isDemoMode) (leads.size - visible.size) else (total - visible.size).coerceAtLeast(0)

    LazyColumn(Modifier.fillMaxSize().testTag("leads_screen"), contentPadding = PaddingValues(bottom = NavBarClearance)) {
        item {
            Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp)) {
                Text("Shoot leads", style = MaterialTheme.typography.headlineLarge, color = Aurora.TextPrimary)
                Text("Clients describing shoots they need. Respond first, win the booking.",
                    style = MaterialTheme.typography.bodyMedium, color = Aurora.TextSecondary)
            }
        }
        if (visible.isEmpty()) {
            item { EmptyState(Icons.Rounded.Bolt, "No open leads right now", "New shoot requests appear here as clients post them.") }
        }
        items(visible, key = { it.id }) { lead ->
            GlassCard(Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth().testTag("lead_${lead.id}")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(lead.eventType, Aurora.Pink)
                    Spacer(Modifier.weight(1f))
                    Text(if (lead.budget > 0) "Budget ${formatInrShort(lead.budget)}" else "Budget open", style = MaterialTheme.typography.titleSmall, color = Aurora.Mint)
                }
                Spacer(Modifier.height(10.dp))
                Text(lead.description, style = MaterialTheme.typography.bodyLarge, color = Aurora.TextPrimary)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Place, null, tint = Aurora.TextTertiary, modifier = Modifier.size(15.dp))
                    Text(" ${lead.location}", style = MaterialTheme.typography.labelMedium, color = Aurora.TextSecondary, modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (lead.dateDetail.isNotBlank()) {
                        Icon(Icons.Rounded.Schedule, null, tint = Aurora.TextTertiary, modifier = Modifier.size(15.dp))
                        Text(" ${lead.dateDetail}", style = MaterialTheme.typography.labelMedium, color = Aurora.TextSecondary)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(lead.customerName, style = MaterialTheme.typography.labelMedium, color = Aurora.TextTertiary, modifier = Modifier.weight(1f))
                    GradientButton("Respond", { viewModel.respondToLead(lead); onChat(lead.customerId) }, height = 42.dp,
                        icon = Icons.Rounded.Reply)
                }
            }
        }
        if (!isPro && hidden > 0) {
            item {
                GlassCard(Modifier.padding(20.dp).fillMaxWidth(), highlight = true, onClick = onPro) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lock, null, tint = Aurora.Amber)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("$hidden more lead${if (hidden == 1) "" else "s"} waiting", style = MaterialTheme.typography.titleMedium, color = Aurora.TextPrimary)
                            Text("Unlock every lead with Creator Pro.", style = MaterialTheme.typography.bodySmall, color = Aurora.TextSecondary)
                        }
                        ProBadge()
                    }
                }
            }
        }
    }
}

// ============================================================================ EDIT PROFILE

@Composable
fun EditProfileScreen(viewModel: FokalViewModel, onBack: () -> Unit) {
    val profile by viewModel.currentUserProfile.collectAsStateWithLifecycle()
    val creator by viewModel.currentCreatorDetails.collectAsStateWithLifecycle()
    val upi by viewModel.myUpiId.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadMyUpiId() }
    val c = creator ?: return
    var name by remember(profile) { mutableStateOf(profile?.name.orEmpty()) }
    var city by remember(profile) { mutableStateOf(profile?.city.orEmpty()) }
    var headline by remember(c.id) { mutableStateOf(c.headline) }
    var bio by remember(c.id) { mutableStateOf(c.bio) }
    var price by remember(c.id) { mutableStateOf(c.startingPrice.toLong().toString()) }
    var years by remember(c.id) { mutableStateOf(c.yearsOfExperience.toString()) }
    var instagram by remember(c.id) { mutableStateOf(c.instagram) }
    var upiId by remember(upi) { mutableStateOf(upi) }
    var type by remember(c.id) { mutableStateOf(c.creatorType) }
    var skills by remember(c.id) { mutableStateOf(c.specialties.toSet()) }
    var saving by remember { mutableStateOf(false) }
    val priceValue = price.toDoubleOrNull()
    val upiValid = upiId.isBlank() || Regex("^[A-Za-z0-9._-]{2,128}@[A-Za-z][A-Za-z0-9.-]{1,64}$").matches(upiId.trim())

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar("Profile & pricing", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AuroraTextField(name, { name = it.take(60) }, "Display name", testTag = "edit_name")
            AuroraTextField(city, { city = it.take(40) }, "City", leadingIcon = Icons.Rounded.Place)
            AuroraTextField(headline, { headline = it.take(80) }, "Headline", placeholder = "e.g. Cinematic weddings that feel like films", testTag = "edit_headline")
            AuroraTextField(bio, { bio = it.take(800) }, "About you", singleLine = false, minLines = 4)
            Text("What you shoot", style = MaterialTheme.typography.labelMedium, color = Aurora.TextSecondary)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Photographer", "Videographer", "Both").forEach { t -> AuroraChip(t, type == t, { type = t }) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Categories.forEach { cat ->
                    AuroraChip(cat.name, cat.name in skills, { skills = if (cat.name in skills) skills - cat.name else skills + cat.name })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AuroraTextField(price, { price = it.filter(Char::isDigit).take(7) }, "Starting price (₹)", Modifier.weight(1f),
                    keyboardType = KeyboardType.Number, isError = priceValue == null || priceValue < 500, testTag = "edit_price")
                AuroraTextField(years, { years = it.filter(Char::isDigit).take(2) }, "Years", Modifier.weight(0.6f), keyboardType = KeyboardType.Number)
            }
            AuroraTextField(instagram, { instagram = it.take(80) }, "Instagram", placeholder = "@yourhandle", leadingIcon = Icons.Rounded.AlternateEmail)
            AuroraTextField(upiId, { upiId = it.trim().take(120) }, "UPI ID for payments", placeholder = "name@okaxis",
                leadingIcon = Icons.Rounded.CurrencyRupee, isError = !upiValid, testTag = "edit_upi")
            Text("Clients see your UPI ID only after you accept their booking. Payments go straight to you — FokalPoint takes no commission.",
                style = MaterialTheme.typography.bodySmall, color = Aurora.TextTertiary)
            Spacer(Modifier.height(8.dp))
        }
        GradientButton(
            "Save profile",
            {
                saving = true
                viewModel.saveCreatorProfile(
                    FokalViewModel.ProfileDraft(name, city, headline, bio, priceValue ?: c.startingPrice, skills.joinToString(", ").ifBlank { type },
                        type, years.toIntOrNull() ?: 0, instagram, upiId)
                ) { ok -> saving = false; if (ok) onBack() }
            },
            Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp).testTag("save_profile"),
            enabled = name.isNotBlank() && priceValue != null && priceValue >= 500 && upiValid,
            loading = saving
        )
    }
}

// ============================================================================ POST REQUEST (customer)

@Composable
fun PostRequestScreen(viewModel: FokalViewModel, onBack: () -> Unit) {
    var type by remember { mutableStateOf("Wedding") }
    var location by remember { mutableStateOf("") }
    var budget by remember { mutableStateOf("") }
    var timeframe by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var posting by remember { mutableStateOf(false) }
    var detecting by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar("Post a shoot request", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Creators who match will message you with quotes.", style = MaterialTheme.typography.bodyMedium, color = Aurora.TextSecondary)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Categories.forEach { AuroraChip(it.name, type == it.name, { type = it.name }, icon = it.icon) }
            }
            AuroraTextField(location, { location = it.take(80) }, "Where?", placeholder = "City or venue", leadingIcon = Icons.Rounded.Place,
                trailing = {
                    Text(if (detecting) "…" else "Use mine", style = MaterialTheme.typography.labelMedium, color = Aurora.VioletSoft,
                        modifier = Modifier.clickable {
                            detecting = true
                            viewModel.detectUserCity { c -> detecting = false; if (c != null) location = "${c.name}, ${c.state}" }
                        })
                }, testTag = "request_location")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AuroraTextField(budget, { budget = it.filter(Char::isDigit).take(8) }, "Budget (₹)", Modifier.weight(1f), keyboardType = KeyboardType.Number)
                AuroraTextField(timeframe, { timeframe = it.take(40) }, "When?", Modifier.weight(1f), placeholder = "e.g. 14 Feb")
            }
            AuroraTextField(description, { description = it.take(1000) }, "Describe your shoot", singleLine = false, minLines = 4,
                placeholder = "Guests, style, must-have moments…", testTag = "request_description")
        }
        GradientButton(
            "Post request",
            {
                posting = true
                viewModel.postShootAlert(type, location, budget.toDoubleOrNull() ?: 0.0, timeframe, description) { ok ->
                    posting = false
                    if (ok) { viewModel.toast.value = "Posted! Creators will reach out in your inbox."; onBack() }
                }
            },
            Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp).testTag("post_request"),
            enabled = location.isNotBlank() && description.length >= 10, loading = posting, icon = Icons.Rounded.Bolt
        )
    }
}

@Suppress("unused")
private val circle = CircleShape
