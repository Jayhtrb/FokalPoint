package com.fokalpoint.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fokalpoint.app.data.model.Creator
import com.fokalpoint.app.ui.components.*
import com.fokalpoint.app.ui.navigation.NavBarClearance
import com.fokalpoint.app.ui.theme.Aurora
import com.fokalpoint.app.ui.viewmodel.FokalViewModel
import java.util.Calendar

data class Category(val name: String, val icon: ImageVector)

val Categories = listOf(
    Category("Wedding", Icons.Rounded.Favorite),
    Category("Pre-Wedding", Icons.Rounded.AutoAwesome),
    Category("Portrait", Icons.Rounded.Face),
    Category("Fashion", Icons.Rounded.Checkroom),
    Category("Events", Icons.Rounded.Celebration),
    Category("Corporate", Icons.Rounded.BusinessCenter),
    Category("Maternity", Icons.Rounded.ChildCare),
    Category("Product", Icons.Rounded.ShoppingBag),
    Category("Films", Icons.Rounded.Movie)
)

// ============================================================================ HOME

@Composable
fun HomeScreen(
    viewModel: FokalViewModel,
    onCreator: (String) -> Unit,
    onExplore: (String) -> Unit,
    onPostRequest: () -> Unit,
    onSaved: () -> Unit
) {
    val profile by viewModel.currentUserProfile.collectAsStateWithLifecycle()
    val featured by viewModel.featuredCreators.collectAsStateWithLifecycle()
    val top by viewModel.topRatedCreators.collectAsStateWithLifecycle()
    val inspiration by viewModel.inspiration.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when (hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }

    LazyColumn(
        Modifier.fillMaxSize().testTag("home_screen"),
        contentPadding = PaddingValues(bottom = NavBarClearance)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 16.dp, top = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(greeting.uppercase(), style = MaterialTheme.typography.labelSmall, color = Aurora.TextTertiary)
                    Spacer(Modifier.height(2.dp))
                    Text(profile?.name?.substringBefore(' ')?.ifBlank { null } ?: "Welcome",
                        style = MaterialTheme.typography.headlineMedium, color = Aurora.TextPrimary)
                }
                CircleIconButton(Icons.Rounded.FavoriteBorder, "Saved", onSaved)
                Spacer(Modifier.width(10.dp))
                Avatar(profile?.profileImage.orEmpty(), profile?.name.orEmpty(), 44.dp)
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
                GradientText("Every moment,", MaterialTheme.typography.displaySmall)
                Text("in focus.", style = MaterialTheme.typography.displaySmall, color = Aurora.TextPrimary)
                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(CircleShape)
                        .background(Aurora.Glass)
                        .border(1.dp, Aurora.GlassBorder, CircleShape)
                        .clickable { onExplore("") }
                        .padding(horizontal = 18.dp)
                        .testTag("home_search"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Search, null, tint = Aurora.TextSecondary)
                    Spacer(Modifier.width(12.dp))
                    Text("Search photographers, styles, cities", style = MaterialTheme.typography.bodyLarge, color = Aurora.TextTertiary,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.size(34.dp).clip(CircleShape).background(Aurora.Signature), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Tune, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(Categories) { cat -> CategoryTile(cat) { onExplore(cat.name) } }
            }
        }
        if (featured.isNotEmpty()) {
            item {
                Spacer(Modifier.height(28.dp))
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("Featured creators", Modifier.weight(1f))
                    ProBadge()
                }
                Spacer(Modifier.height(14.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(featured, key = { it.id }) { c ->
                        FeaturedCreatorCard(c, viewModel, c.id in favorites, { viewModel.toggleFavorite(c.id) }) { onCreator(c.id) }
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(24.dp))
            GlassCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), highlight = true, onClick = onPostRequest) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Aurora.Signature), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Bolt, null, tint = Color.White)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Post a shoot request", style = MaterialTheme.typography.titleMedium, color = Aurora.TextPrimary)
                        Text("Describe your event and let creators come to you.", style = MaterialTheme.typography.bodySmall, color = Aurora.TextSecondary)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = Aurora.TextSecondary)
                }
            }
        }
        if (inspiration.isNotEmpty()) {
            item {
                Spacer(Modifier.height(28.dp))
                SectionHeader("Inspiration", Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(14.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(inspiration.take(12), key = { it.id }) { photo ->
                        Box(Modifier.size(width = 132.dp, height = 176.dp).clip(RoundedCornerShape(20.dp)).clickable { onCreator(photo.creatorId) }) {
                            FokalImage(photo.mediaUrl, photo.title, Modifier.fillMaxSize())
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))))
                            Text(photo.category, style = MaterialTheme.typography.labelMedium, color = Color.White,
                                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
                        }
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(28.dp))
            SectionHeader("Top rated near you", Modifier.padding(horizontal = 20.dp), action = "See all") { onExplore("") }
            Spacer(Modifier.height(10.dp))
        }
        if (top.isEmpty()) {
            item {
                EmptyState(Icons.Rounded.CameraAlt, "Creators are on their way",
                    "We're onboarding photographers in your city. Post a shoot request and we'll match you.",
                    action = "Post a request", onAction = onPostRequest)
            }
        }
        items(top.take(8), key = { "top_${it.id}" }) { c ->
            CreatorRow(c, viewModel, Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) { onCreator(c.id) }
        }
    }
}

@Composable
private fun CategoryTile(category: Category, onClick: () -> Unit) {
    Column(
        Modifier.width(76.dp).clickable(onClick = onClick).testTag("category_${category.name}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(22.dp)).background(Aurora.Glass).border(1.dp, Aurora.GlassBorder, RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center
        ) { Icon(category.icon, null, tint = Aurora.VioletSoft, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.height(8.dp))
        Text(category.name, style = MaterialTheme.typography.labelSmall, color = Aurora.TextSecondary, maxLines = 1)
    }
}

@Composable
fun FeaturedCreatorCard(c: Creator, viewModel: FokalViewModel, isFavorite: Boolean, onFavorite: () -> Unit, onClick: () -> Unit) {
    val name = viewModel.getCreatorNameSync(c.id)
    Box(
        Modifier
            .size(width = 264.dp, height = 340.dp)
            .clip(RoundedCornerShape(28.dp))
            .border(1.dp, Aurora.GlassBorder, RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
            .testTag("featured_${c.id}")
    ) {
        FokalImage(c.coverImage.ifBlank { viewModel.getCreatorAvatarSync(c.id) }, name, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0xF207070B))))
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ProBadge()
            Spacer(Modifier.weight(1f))
            CircleIconButton(if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Save", onFavorite,
                tint = if (isFavorite) Aurora.Pink else Color.White, size = 38.dp)
        }
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(viewModel.getCreatorAvatarSync(c.id), name, 34.dp, proRing = true)
                Spacer(Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1)
                        if (c.verified) { Spacer(Modifier.width(4.dp)); VerifiedMark() }
                    }
                    Text(viewModel.getCreatorCitySync(c.id), style = MaterialTheme.typography.bodySmall, color = Aurora.TextSecondary)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(c.headline.ifBlank { c.skillset }, style = MaterialTheme.typography.bodyMedium, color = Aurora.TextPrimary,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RatingLabel(c.rating, c.reviewCount)
                Spacer(Modifier.weight(1f))
                Text("from ", style = MaterialTheme.typography.bodySmall, color = Aurora.TextTertiary)
                Text(formatInr(c.startingPrice), style = MaterialTheme.typography.titleSmall, color = Aurora.TextPrimary)
            }
        }
    }
}

@Composable
fun CreatorRow(c: Creator, viewModel: FokalViewModel, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val name = viewModel.getCreatorNameSync(c.id)
    GlassCard(modifier.fillMaxWidth().testTag("creator_row_${c.id}"), onClick = onClick, contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(84.dp).clip(RoundedCornerShape(18.dp))) {
                FokalImage(c.coverImage.ifBlank { viewModel.getCreatorAvatarSync(c.id) }, name, Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.titleMedium, color = Aurora.TextPrimary, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (c.verified) { Spacer(Modifier.width(4.dp)); VerifiedMark() }
                    if (c.isPro) { Spacer(Modifier.width(6.dp)); ProBadge(compact = true) }
                }
                Spacer(Modifier.height(2.dp))
                Text(c.headline.ifBlank { c.skillset }, style = MaterialTheme.typography.bodySmall, color = Aurora.TextSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RatingLabel(c.rating, c.reviewCount)
                    val city = viewModel.getCreatorCitySync(c.id)
                    if (city.isNotBlank()) {
                        Text("  ·  ", color = Aurora.TextTertiary, style = MaterialTheme.typography.labelMedium)
                        Icon(Icons.Rounded.Place, null, tint = Aurora.TextTertiary, modifier = Modifier.size(13.dp))
                        Text(city, style = MaterialTheme.typography.labelMedium, color = Aurora.TextSecondary, maxLines = 1)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("from", style = MaterialTheme.typography.labelSmall, color = Aurora.TextTertiary)
                Text(formatInrShort(c.startingPrice), style = MaterialTheme.typography.titleMedium, color = Aurora.TextPrimary)
            }
        }
    }
}

// ============================================================================ EXPLORE

@Composable
fun ExploreScreen(viewModel: FokalViewModel, onCreator: (String) -> Unit) {
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val category by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val city by viewModel.filterCity.collectAsStateWithLifecycle()
    val budget by viewModel.filterMaxBudget.collectAsStateWithLifecycle()
    val results by viewModel.filteredCreators.collectAsStateWithLifecycle()
    val cities by viewModel.cities.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize().testTag("explore_screen"), contentPadding = PaddingValues(bottom = NavBarClearance)) {
        item {
            Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp)) {
                Text("Explore", style = MaterialTheme.typography.headlineLarge, color = Aurora.TextPrimary)
                Spacer(Modifier.height(16.dp))
                AuroraTextField(query, { viewModel.searchQuery.value = it }, label = "", placeholder = "Name, style or city",
                    leadingIcon = Icons.Rounded.Search, testTag = "explore_search",
                    trailing = if (query.isNotEmpty()) ({
                        Icon(Icons.Rounded.Close, "Clear", tint = Aurora.TextSecondary,
                            modifier = Modifier.size(20.dp).clickable { viewModel.searchQuery.value = "" })
                    }) else null)
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AuroraChip("All", category.isEmpty(), { viewModel.selectedCategory.value = "" })
                Categories.forEach { cat ->
                    AuroraChip(cat.name, category == cat.name, { viewModel.selectedCategory.value = if (category == cat.name) "" else cat.name }, icon = cat.icon)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to "Any budget", 15000.0 to "Under ₹15K", 30000.0 to "Under ₹30K", 50000.0 to "Under ₹50K").forEach { (value, label) ->
                    AuroraChip(label, budget == value, { viewModel.filterMaxBudget.value = value }, icon = if (value == null) Icons.Rounded.Payments else null)
                }
                cities.forEach { c ->
                    AuroraChip(c, city == c, { viewModel.filterCity.value = if (city == c) "" else c }, icon = Icons.Rounded.Place)
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("${results.size} creator${if (results.size == 1) "" else "s"}", style = MaterialTheme.typography.labelMedium,
                color = Aurora.TextTertiary, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(6.dp))
        }
        if (results.isEmpty()) {
            item {
                EmptyState(Icons.Rounded.SearchOff, "No matches yet", "Try another style, city or budget.",
                    action = "Clear filters", onAction = { viewModel.clearFilters() })
            }
        }
        items(results, key = { it.id }) { c ->
            CreatorRow(c, viewModel, Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) { onCreator(c.id) }
        }
    }
}

// ============================================================================ SAVED

@Composable
fun SavedScreen(viewModel: FokalViewModel, onBack: () -> Unit, onCreator: (String) -> Unit) {
    val saved by viewModel.favoriteCreators.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TopBar("Saved", onBack)
        if (saved.isEmpty()) {
            EmptyState(Icons.Rounded.FavoriteBorder, "Nothing saved yet", "Tap the heart on any creator to keep them here.")
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            items(saved, key = { it.id }) { c -> CreatorRow(c, viewModel, Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) { onCreator(c.id) } }
        }
    }
}
