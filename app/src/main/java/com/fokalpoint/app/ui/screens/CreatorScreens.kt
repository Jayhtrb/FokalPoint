package com.fokalpoint.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fokalpoint.app.data.model.Creator
import com.fokalpoint.app.data.model.ShootPackage
import com.fokalpoint.app.ui.components.*
import com.fokalpoint.app.ui.theme.Aurora
import com.fokalpoint.app.ui.utils.BookingDates
import com.fokalpoint.app.ui.viewmodel.FokalViewModel

// ============================================================================ PUBLIC PROFILE

@Composable
fun CreatorProfileScreen(creatorId: String, viewModel: FokalViewModel, onBack: () -> Unit, onBook: () -> Unit, onChat: () -> Unit) {
    val creator by remember(creatorId) { viewModel.creator(creatorId) }.collectAsStateWithLifecycle(null)
    val photos by remember(creatorId) { viewModel.portfolio(creatorId) }.collectAsStateWithLifecycle(emptyList())
    val reviews by remember(creatorId) { viewModel.reviews(creatorId) }.collectAsStateWithLifecycle(emptyList())
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val me by viewModel.currentUserId.collectAsStateWithLifecycle()
    var viewing by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(creatorId) {
        viewModel.selectedCreatorId.value = creatorId
        viewModel.refreshCreatorDetail(creatorId)
    }
    val c = creator ?: return Box(Modifier.fillMaxSize()) { TopBar("", onBack) }
    val name = viewModel.getCreatorNameSync(c.id)
    val isMe = c.id == me

    Box(Modifier.fillMaxSize().testTag("creator_profile")) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 120.dp)) {
            Box(Modifier.fillMaxWidth().height(420.dp)) {
                FokalImage(c.coverImage.ifBlank { photos.firstOrNull()?.mediaUrl ?: viewModel.getCreatorAvatarSync(c.id) }, name, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x6607070B), 0.35f to Color.Transparent, 1f to Aurora.Void)))
                Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp)) {
                    Avatar(viewModel.getCreatorAvatarSync(c.id), name, 76.dp, proRing = c.isPro)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.headlineLarge, color = Aurora.TextPrimary)
                        if (c.verified) { Spacer(Modifier.width(6.dp)); VerifiedMark(Modifier.size(22.dp)) }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(c.headline.ifBlank { c.skillset }, style = MaterialTheme.typography.bodyLarge, color = Aurora.TextSecondary)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (c.isPro) ProBadge()
                        val city = viewModel.getCreatorCitySync(c.id)
                        if (city.isNotBlank()) Pill(city, Aurora.Cyan)
                        Pill(c.experienceLevel, Aurora.VioletSoft)
                    }
                }
            }

            Row(Modifier.padding(horizontal = 20.dp, vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(if (c.reviewCount == 0) "New" else String.format(java.util.Locale.US, "%.1f★", c.rating), "${c.reviewCount} reviews", Modifier.weight(1f), Aurora.Amber)
                StatTile("${c.yearsOfExperience}y", "experience", Modifier.weight(1f))
                StatTile(formatInrShort(c.startingPrice), "starting", Modifier.weight(1f), Aurora.Cyan)
            }

            if (c.bio.isNotBlank()) {
                Text(c.bio, style = MaterialTheme.typography.bodyLarge, color = Aurora.TextSecondary, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(16.dp))
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                c.specialties.forEach { Pill(it, Aurora.TextSecondary) }
            }

            if (photos.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                SectionHeader("Portfolio", Modifier.padding(horizontal = 20.dp), action = "${photos.size} photos")
                Spacer(Modifier.height(12.dp))
                PortfolioMosaic(photos.map { it.mediaUrl }, Modifier.padding(horizontal = 20.dp)) { viewing = it }
            }

            Spacer(Modifier.height(28.dp))
            SectionHeader("Packages", Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(12.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(ShootPackage.entries) { pkg -> PackageCard(pkg, c, selected = pkg == ShootPackage.Signature, modifier = Modifier.width(220.dp)) { if (!isMe) onBook() } }
            }

            Spacer(Modifier.height(28.dp))
            SectionHeader("Reviews", Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(12.dp))
            if (reviews.isEmpty()) {
                Text("No reviews yet — be the first to book.", style = MaterialTheme.typography.bodyMedium, color = Aurora.TextTertiary,
                    modifier = Modifier.padding(horizontal = 20.dp))
            }
            reviews.take(6).forEach { r ->
                GlassCard(Modifier.padding(horizontal = 20.dp, vertical = 5.dp).fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar("", r.customerName, 34.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.customerName, style = MaterialTheme.typography.titleSmall, color = Aurora.TextPrimary)
                            Text(java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(r.createdAt)),
                                style = MaterialTheme.typography.bodySmall, color = Aurora.TextTertiary)
                        }
                        Row { repeat(5) { i -> Icon(Icons.Rounded.Star, null, tint = if (i < r.rating) Aurora.Amber else Aurora.Hairline, modifier = Modifier.size(14.dp)) } }
                    }
                    if (r.review.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(r.review, style = MaterialTheme.typography.bodyMedium, color = Aurora.TextSecondary)
                    }
                }
            }
        }

        Row(Modifier.statusBarsPadding().padding(16.dp).fillMaxWidth()) {
            CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            Spacer(Modifier.weight(1f))
            if (!isMe) {
                val fav = c.id in favorites
                CircleIconButton(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Save",
                    { viewModel.toggleFavorite(c.id) }, tint = if (fav) Aurora.Pink else Color.White)
            }
        }

        if (!isMe) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Aurora.Void, Aurora.Void)))
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleIconButton(Icons.Rounded.ChatBubbleOutline, "Message", onChat, size = 56.dp)
                Spacer(Modifier.width(12.dp))
                GradientButton("Check availability", onBook, Modifier.weight(1f).testTag("book_cta"), icon = Icons.Rounded.CalendarMonth)
            }
        }
    }

    viewing?.let { src ->
        Dialog(onDismissRequest = { viewing = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color(0xF2000000)).clickable { viewing = null }, contentAlignment = Alignment.Center) {
                FokalImage(src, null, Modifier.fillMaxWidth().aspectRatio(0.8f), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
            }
        }
    }
}

@Composable
fun PortfolioMosaic(images: List<String>, modifier: Modifier = Modifier, onOpen: (String) -> Unit) {
    val shown = images.take(6)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        shown.chunked(3).forEachIndexed { row, chunk ->
            Row(Modifier.fillMaxWidth().height(if (row == 0) 220.dp else 130.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chunk.forEachIndexed { i, src ->
                    val weight = if (row == 0 && i == 0) 2f else 1f
                    Box(Modifier.weight(weight).fillMaxHeight().clip(RoundedCornerShape(18.dp)).clickable { onOpen(src) }) {
                        FokalImage(src, null, Modifier.fillMaxSize())
                    }
                }
                repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun PackageCard(pkg: ShootPackage, creator: Creator, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    GlassCard(modifier.testTag("package_${pkg.name}"), highlight = selected, onClick = onClick, contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(pkg.title, style = MaterialTheme.typography.titleMedium, color = Aurora.TextPrimary, modifier = Modifier.weight(1f))
            if (pkg == ShootPackage.Signature) Pill("Popular", Aurora.Pink)
        }
        Spacer(Modifier.height(6.dp))
        GradientText(formatInr(pkg.price(creator.startingPrice)), MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        pkg.perks.forEach { perk ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Check, null, tint = Aurora.Mint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(perk, style = MaterialTheme.typography.bodySmall, color = Aurora.TextSecondary)
            }
        }
    }
}

// ============================================================================ BOOKING REQUEST

private val TimeSlots = listOf("07:00", "09:00", "11:00", "14:00", "16:00", "18:00")

@Composable
fun BookingRequestScreen(creatorId: String, viewModel: FokalViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    val creator by remember(creatorId) { viewModel.creator(creatorId) }.collectAsStateWithLifecycle(null)
    val blocked by viewModel.blockedDatesState.collectAsStateWithLifecycle()
    val booked by remember(creatorId) { viewModel.activeCreatorBookings(creatorId) }.collectAsStateWithLifecycle(emptyList())
    var pkg by remember { mutableStateOf(ShootPackage.Signature) }
    var date by remember { mutableStateOf<String?>(null) }
    var time by remember { mutableStateOf("16:00") }
    var eventType by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }

    val c = creator ?: return
    val unavailable = remember(blocked, booked) {
        blocked[creatorId].orEmpty().toSet() + booked.filter { it.status == "Accepted" || it.status == "Confirmed" }.map { it.date }
    }
    val days = remember { BookingDates.upcomingDays(45) }
    val total = pkg.price(c.startingPrice)
    val name = viewModel.getCreatorNameSync(c.id)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 130.dp)) {
            TopBar("Book $name".let { if (it.length > 22) "Request a shoot" else it }, onBack)

            Text("Choose a package", style = MaterialTheme.typography.titleMedium, color = Aurora.TextPrimary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ShootPackage.entries.forEach { p ->
                    GlassCard(Modifier.fillMaxWidth().testTag("pick_${p.name}"), highlight = p == pkg, onClick = { pkg = p }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(22.dp).clip(CircleShape).border(2.dp, if (p == pkg) Aurora.Signature else Brush.linearGradient(listOf(Aurora.Hairline, Aurora.Hairline)), CircleShape),
                                contentAlignment = Alignment.Center) {
                                if (p == pkg) Box(Modifier.size(10.dp).clip(CircleShape).background(Aurora.Signature))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.title, style = MaterialTheme.typography.titleMedium, color = Aurora.TextPrimary)
                                Text(p.perks.take(2).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Aurora.TextSecondary)
                            }
                            Text(formatInr(p.price(c.startingPrice)), style = MaterialTheme.typography.titleMedium, color = Aurora.TextPrimary)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Pick a date", style = MaterialTheme.typography.titleMedium, color = Aurora.TextPrimary, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(10.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(days) { d ->
                    val off = d in unavailable
                    val selected = d == date
                    val (dow, rest) = BookingDates.chipLabel(d).split(" ", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                    Column(
                        Modifier
                            .width(64.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (selected) Aurora.Signature else Brush.linearGradient(listOf(Aurora.Glass, Aurora.Glass)))
                            .border(1.dp, if (selected) Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)) else Aurora.GlassBorder, RoundedCornerShape(18.dp))
                            .clickable(enabled = !off) { date = d }
                            .padding(vertical = 12.dp)
                            .testTag("date_$d"),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(dow, style = MaterialTheme.typography.labelSmall, color = if (off) Aurora.TextTertiary.copy(alpha = 0.5f) else if (selected) Color.White else Aurora.TextTertiary)
                        Text(rest.substringBefore(' '), style = MaterialTheme.typography.titleLarge,
                            color = if (off) Aurora.TextTertiary.copy(alpha = 0.4f) else Color.White)
                        Text(if (off) "Booked" else rest.substringAfter(' '), style = MaterialTheme.typography.labelSmall,
                            color = if (off) Aurora.Coral.copy(alpha = 0.7f) else if (selected) Color.White else Aurora.TextTertiary)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Start time", style = MaterialTheme.typography.titleMedium, color = Aurora.TextPrimary, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeSlots.forEach { t -> AuroraChip(t, t == time, { time = t }) }
            }

            Spacer(Modifier.height(24.dp))
            Column(Modifier.padding(horizontal = 20.dp)) {
                AuroraTextField(eventType, { eventType = it.take(60) }, "What's the occasion?", placeholder = "e.g. Wedding reception, brand launch", testTag = "booking_event")
                Spacer(Modifier.height(16.dp))
                AuroraTextField(notes, { notes = it.take(1000) }, "Notes for $name", placeholder = "Venue, vibe, must-have shots…",
                    singleLine = false, minLines = 3, testTag = "booking_notes")
                Spacer(Modifier.height(16.dp))
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.Shield, null, tint = Aurora.Mint, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("No payment now. $name confirms availability first; then you pay them directly by UPI inside the app.",
                            style = MaterialTheme.typography.bodySmall, color = Aurora.TextSecondary)
                    }
                }
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Aurora.Void, Aurora.Void)))
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Total", style = MaterialTheme.typography.labelSmall, color = Aurora.TextTertiary)
                Text(formatInr(total), style = MaterialTheme.typography.headlineSmall, color = Aurora.TextPrimary)
            }
            GradientButton(
                if (date == null) "Pick a date" else "Send request",
                onClick = {
                    sending = true
                    viewModel.requestBooking(c.id, pkg, date!!, time, notes, eventType.ifBlank { pkg.title + " shoot" }) { ok ->
                        sending = false
                        if (ok) sent = true
                    }
                },
                enabled = date != null, loading = sending,
                modifier = Modifier.testTag("send_request")
            )
        }
    }

    if (sent) {
        Dialog(onDismissRequest = { sent = false; onDone() }) {
            GlassCard(Modifier.fillMaxWidth(), highlight = true, contentPadding = PaddingValues(24.dp)) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(Aurora.Signature).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text("Request sent", style = MaterialTheme.typography.headlineSmall, color = Aurora.TextPrimary, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(6.dp))
                Text("$name will confirm your ${pkg.title} shoot on ${BookingDates.chipLabel(date.orEmpty())}. We'll let you know.",
                    style = MaterialTheme.typography.bodyMedium, color = Aurora.TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                GradientButton("View my bookings", { sent = false; onDone() }, Modifier.fillMaxWidth().testTag("view_bookings"))
            }
        }
    }
}

@Suppress("unused")
private val bold = FontWeight.Bold
