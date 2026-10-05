package com.fokalpoint.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fokalpoint.app.data.model.Booking
import com.fokalpoint.app.data.model.Message
import com.fokalpoint.app.ui.components.*
import com.fokalpoint.app.ui.navigation.NavBarClearance
import com.fokalpoint.app.ui.theme.Aurora
import com.fokalpoint.app.ui.utils.BookingDates
import com.fokalpoint.app.ui.viewmodel.FokalViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ============================================================================ BOOKINGS

@Composable
fun BookingsScreen(viewModel: FokalViewModel, onChat: (String) -> Unit, onCreator: (String) -> Unit, onExplore: () -> Unit) {
    val role by viewModel.currentUserRole.collectAsStateWithLifecycle()
    val bookings by viewModel.bookingsList.collectAsStateWithLifecycle()
    val reviewed by viewModel.reviewedBookingIds.collectAsStateWithLifecycle()
    val isCreator = role == "Creator"
    var tab by remember { mutableIntStateOf(0) }
    var paying by remember { mutableStateOf<Booking?>(null) }
    var reviewing by remember { mutableStateOf<Booking?>(null) }

    val tabs = if (isCreator) listOf("Requests", "Upcoming", "Past") else listOf("Upcoming", "Past")
    val shown = remember(bookings, tab, isCreator) {
        val label = tabs.getOrElse(tab) { tabs.first() }
        when (label) {
            "Requests" -> bookings.filter { it.status == "Pending" }
            "Upcoming" -> bookings.filter { if (isCreator) it.status == "Accepted" || it.status == "Confirmed" else it.isActive }
            else -> bookings.filter { !it.isActive }.reversed()
        }
    }

    LazyColumn(Modifier.fillMaxSize().testTag("bookings_screen"), contentPadding = PaddingValues(bottom = NavBarClearance)) {
        item {
            Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp)) {
                Text(if (isCreator) "Requests" else "Bookings", style = MaterialTheme.typography.headlineLarge, color = Aurora.TextPrimary)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tabs.forEachIndexed { i, t ->
                        val count = when (t) {
                            "Requests" -> bookings.count { it.status == "Pending" }
                            else -> 0
                        }
                        AuroraChip(if (count > 0) "$t · $count" else t, tab == i, { tab = i })
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                EmptyState(
                    Icons.Rounded.EventAvailable,
                    if (isCreator) "All clear" else "No bookings yet",
                    if (isCreator) "New booking requests land here. Browse leads to find more work."
                    else "Find a creator you love and request a date.",
                    action = if (isCreator) "Browse leads" else "Explore creators", onAction = onExplore
                )
            }
        }
        items(shown, key = { it.id }) { b ->
            BookingCard(
                b, viewModel, isCreator, alreadyReviewed = b.id in reviewed,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                onChat = { onChat(if (isCreator) b.customerId else b.creatorId) },
                onOpen = { if (!isCreator) onCreator(b.creatorId) },
                onPay = { paying = b },
                onReview = { reviewing = b }
            )
        }
    }

    paying?.let { PaymentDialog(it, viewModel) { paying = null } }
    reviewing?.let { b -> ReviewDialog(viewModel.getCreatorNameSync(b.creatorId), { r, t -> viewModel.submitReview(b, r, t) { ok -> if (ok) reviewing = null } }) { reviewing = null } }
}

@Composable
private fun BookingCard(
    b: Booking, viewModel: FokalViewModel, isCreator: Boolean, alreadyReviewed: Boolean, modifier: Modifier,
    onChat: () -> Unit, onOpen: () -> Unit, onPay: () -> Unit, onReview: () -> Unit
) {
    val otherId = if (isCreator) b.customerId else b.creatorId
    val name = viewModel.getCreatorNameSync(otherId)
    GlassCard(modifier.fillMaxWidth().testTag("booking_${b.id}"), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(viewModel.getCreatorAvatarSync(otherId), name, 46.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, color = Aurora.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOf(b.packageName, b.eventType).filter { it.isNotBlank() }.distinct().joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = Aurora.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Pill(statusLabel(b, isCreator), statusColor(b.status))
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CalendarMonth, null, tint = Aurora.TextTertiary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("${BookingDates.chipLabel(b.date)} · ${b.time} · ${b.hours}h", style = MaterialTheme.typography.labelMedium, color = Aurora.TextSecondary,
                modifier = Modifier.weight(1f))
            Text(formatInr(b.price), style = MaterialTheme.typography.titleMedium, color = Aurora.TextPrimary)
        }
        if (b.notes.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text("“${b.notes}”", style = MaterialTheme.typography.bodySmall, color = Aurora.TextTertiary, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isCreator) {
                when (b.status) {
                    "Pending" -> {
                        GradientButton("Accept", { viewModel.updateBookingStatus(b.id, "Accepted") }, height = 44.dp, icon = Icons.Rounded.Check,
                            modifier = Modifier.testTag("accept_${b.id}"))
                        GlassButton("Decline", { viewModel.updateBookingStatus(b.id, "Cancelled") }, height = 44.dp, tint = Aurora.Coral)
                    }
                    "Accepted" -> GradientButton("Payment received", { viewModel.confirmBookingPayment(b.id) }, height = 44.dp,
                        icon = Icons.Rounded.Paid, modifier = Modifier.testTag("confirm_paid_${b.id}"))
                    "Confirmed" -> GradientButton("Mark completed", { viewModel.updateBookingStatus(b.id, "Completed") }, height = 44.dp,
                        icon = Icons.Rounded.TaskAlt, modifier = Modifier.testTag("complete_${b.id}"))
                }
            } else {
                when (b.status) {
                    "Accepted" -> GradientButton("Pay via UPI", onPay, height = 44.dp, icon = Icons.Rounded.CurrencyRupee,
                        modifier = Modifier.testTag("pay_${b.id}"))
                    "Completed" -> if (!alreadyReviewed && b.id > 0) GradientButton("Rate your shoot", onReview, height = 44.dp, icon = Icons.Rounded.Star,
                        modifier = Modifier.testTag("review_${b.id}"))
                }
                if (b.status == "Pending" || b.status == "Accepted") {
                    GlassButton("Cancel", { viewModel.updateBookingStatus(b.id, "Cancelled") }, height = 44.dp, tint = Aurora.Coral)
                }
            }
        }
            Spacer(Modifier.width(8.dp))
            CircleIconButton(Icons.Rounded.ChatBubbleOutline, "Message", onChat, size = 44.dp)
        }
    }
}

private fun statusLabel(b: Booking, isCreator: Boolean): String = when (b.status) {
    "Pending" -> if (isCreator) "New request" else "Awaiting"
    "Accepted" -> if (isCreator) "Awaiting payment" else "Accepted"
    "Confirmed" -> "Paid"
    else -> b.status
}

/** Opens any UPI app (GPay, PhonePe, Paytm…) pre-filled with the creator's UPI ID and amount. */
@Composable
private fun PaymentDialog(booking: Booking, viewModel: FokalViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    var details by remember { mutableStateOf<FokalViewModel.PaymentDetails?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(booking.id) { viewModel.loadPaymentDetails(booking.id) { details = it; loaded = true } }

    Dialog(onDismissRequest = onClose) {
        GlassCard(Modifier.fillMaxWidth(), highlight = true, contentPadding = PaddingValues(22.dp)) {
            Text("Pay your creator", style = MaterialTheme.typography.headlineSmall, color = Aurora.TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("Payments go directly to the creator by UPI. FokalPoint never holds your money.",
                style = MaterialTheme.typography.bodySmall, color = Aurora.TextSecondary)
            Spacer(Modifier.height(18.dp))
            val d = details
            when {
                !loaded -> Text("Loading…", color = Aurora.TextTertiary)
                d == null -> Text("This creator hasn't added a UPI ID yet. Message them to arrange payment.",
                    style = MaterialTheme.typography.bodyMedium, color = Aurora.Amber)
                else -> {
                    GradientText(formatInr(d.amount), MaterialTheme.typography.displaySmall)
                    Text("to ${d.payeeName} · ${d.upiId}", style = MaterialTheme.typography.bodyMedium, color = Aurora.TextSecondary)
                    Spacer(Modifier.height(18.dp))
                    GradientButton("Open UPI app", {
                        val uri = Uri.Builder().scheme("upi").authority("pay")
                            .appendQueryParameter("pa", d.upiId)
                            .appendQueryParameter("pn", d.payeeName)
                            .appendQueryParameter("am", String.format(Locale.US, "%.2f", d.amount))
                            .appendQueryParameter("cu", "INR")
                            .appendQueryParameter("tn", "FokalPoint booking #${booking.id}")
                            .build()
                        try {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, uri), "Pay with"))
                            opened = true
                        } catch (e: ActivityNotFoundException) {
                            viewModel.toast.value = "No UPI app found. Pay ${d.upiId} from any UPI app."
                            opened = true
                        }
                    }, Modifier.fillMaxWidth().testTag("open_upi"), icon = Icons.Rounded.CurrencyRupee)
                    if (opened) {
                        Spacer(Modifier.height(10.dp))
                        GlassButton("I've paid — notify creator", { viewModel.notifyPaymentSent(booking); onClose() },
                            Modifier.fillMaxWidth(), icon = Icons.Rounded.Done)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Close", style = MaterialTheme.typography.labelLarge, color = Aurora.TextSecondary,
                modifier = Modifier.align(Alignment.CenterHorizontally).clip(CircleShape).clickable(onClick = onClose).padding(12.dp))
        }
    }
}

@Composable
private fun ReviewDialog(creatorName: String, onSubmit: (Int, String) -> Unit, onClose: () -> Unit) {
    var rating by remember { mutableIntStateOf(5) }
    var text by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onClose) {
        GlassCard(Modifier.fillMaxWidth(), highlight = true, contentPadding = PaddingValues(22.dp)) {
            Text("How was your shoot?", style = MaterialTheme.typography.headlineSmall, color = Aurora.TextPrimary)
            Text("with $creatorName", style = MaterialTheme.typography.bodyMedium, color = Aurora.TextSecondary)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                (1..5).forEach { i ->
                    Icon(Icons.Rounded.Star, "$i stars", tint = if (i <= rating) Aurora.Amber else Aurora.Hairline,
                        modifier = Modifier.size(40.dp).clip(CircleShape).clickable { rating = i }.padding(2.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            AuroraTextField(text, { text = it.take(1000) }, "", placeholder = "Share what made it special…", singleLine = false, minLines = 3)
            Spacer(Modifier.height(16.dp))
            GradientButton("Post review", { onSubmit(rating, text) }, Modifier.fillMaxWidth().testTag("post_review"))
        }
    }
}

// ============================================================================ INBOX

@Composable
fun InboxScreen(viewModel: FokalViewModel, onChat: (String) -> Unit) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val me by viewModel.currentUserId.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize().testTag("inbox_screen"), contentPadding = PaddingValues(bottom = NavBarClearance)) {
        item {
            Text("Inbox", style = MaterialTheme.typography.headlineLarge, color = Aurora.TextPrimary,
                modifier = Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp))
        }
        if (conversations.isEmpty()) {
            item { EmptyState(Icons.Rounded.ChatBubbleOutline, "No conversations yet", "Message a creator to plan your shoot — replies show up here.") }
        }
        items(conversations, key = { it.id }) { m ->
            val partner = if (m.senderId == me) m.receiverId else m.senderId
            val name = viewModel.getCreatorNameSync(partner)
            Row(
                Modifier.fillMaxWidth().clickable { onChat(partner) }.padding(horizontal = 20.dp, vertical = 12.dp).testTag("conversation_$partner"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(viewModel.getCreatorAvatarSync(partner), name, 52.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.titleMedium, color = Aurora.TextPrimary, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(relativeTime(m.createdAt), style = MaterialTheme.typography.labelSmall, color = Aurora.TextTertiary)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text((if (m.senderId == me) "You: " else "") + m.message, style = MaterialTheme.typography.bodyMedium,
                        color = Aurora.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun relativeTime(t: Long): String {
    val diff = System.currentTimeMillis() - t
    return when {
        diff < 60_000 -> "now"
        diff < 3_600_000 -> "${diff / 60_000}m"
        diff < 86_400_000 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t))
        diff < 7 * 86_400_000L -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(t))
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(t))
    }
}

// ============================================================================ CHAT

@Composable
fun ChatScreen(partnerId: String, viewModel: FokalViewModel, onBack: () -> Unit, onProfile: (String) -> Unit) {
    LaunchedEffect(partnerId) { viewModel.selectedChatCreatorId.value = partnerId }
    DisposableEffect(partnerId) { onDispose { if (viewModel.selectedChatCreatorId.value == partnerId) viewModel.selectedChatCreatorId.value = null } }
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val me by viewModel.currentUserId.collectAsStateWithLifecycle()
    val role by viewModel.currentUserRole.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }
    val name = viewModel.getCreatorNameSync(partnerId)

    Column(Modifier.fillMaxSize().imePadding().testTag("chat_screen")) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(12.dp))
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(enabled = role == "Customer") { onProfile(partnerId) }.padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(viewModel.getCreatorAvatarSync(partnerId), name, 40.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(name, style = MaterialTheme.typography.titleMedium, color = Aurora.TextPrimary, maxLines = 1)
                    Text(if (role == "Customer") "View profile" else "Client", style = MaterialTheme.typography.bodySmall, color = Aurora.TextTertiary)
                }
            }
        }
        Divider()
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Text("Say hello to $name. Share your date, location and the look you're going for.",
                        style = MaterialTheme.typography.bodyMedium, color = Aurora.TextTertiary, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp))
                }
            }
            items(messages, key = { it.id }) { m -> Bubble(m, m.senderId == me) }
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            AuroraTextField(draft, { draft = it.take(4000) }, "", Modifier.weight(1f), placeholder = "Message", singleLine = false,
                testTag = "chat_input")
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(54.dp).clip(CircleShape)
                    .background(if (draft.isBlank()) androidx.compose.ui.graphics.SolidColor(Aurora.GlassStrong) else Aurora.Signature)
                    .clickable(enabled = draft.isNotBlank()) { viewModel.sendMessage(draft); draft = "" }
                    .testTag("chat_send"),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.AutoMirrored.Rounded.Send, "Send", tint = Color.White) }
        }
    }
}

@Composable
private fun Bubble(m: Message, mine: Boolean) {
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = if (mine) 22.dp else 6.dp, bottomEnd = if (mine) 6.dp else 22.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .then(if (mine) Modifier.background(Aurora.Signature) else Modifier.background(Aurora.GlassStrong))
                .padding(horizontal = 16.dp, vertical = 11.dp)
        ) {
            Text(m.message, style = MaterialTheme.typography.bodyMedium, color = Color.White)
            Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(m.createdAt)), style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.End))
        }
    }
}
