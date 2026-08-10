package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.Booking
import com.example.ui.viewmodel.FokalViewModel

// Fallback hiltViewModel helper to bridge compilation with standard Jetpack Compose navigation
@Composable
inline fun <reified VM : androidx.lifecycle.ViewModel> hiltViewModel(): VM {
    return androidx.lifecycle.viewmodel.compose.viewModel()
}

@Composable
fun RowScope.QuickActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .height(80.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CreatorDashboard(
    creatorId: String,
    viewModel: FokalViewModel = hiltViewModel()
) {
    val bookings by viewModel.creatorBookings.collectAsState()
    val earnings by viewModel.earnings.collectAsState()
    val upcomingShoots by viewModel.upcomingShoots.collectAsState()
    
    // Resolve colors in safe Composable scope prior to list construction
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Stats Cards
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                listOf(
                    StatCardData(
                        title = "Upcoming",
                        value = upcomingShoots.size.toString(),
                        icon = Icons.Default.CalendarToday,
                        color = primaryColor
                    ),
                    StatCardData(
                        title = "Revenue",
                        value = "₹${String.format("%.0f", earnings.total)}",
                        icon = Icons.Default.Payment,
                        color = secondaryColor
                    ),
                    StatCardData(
                        title = "Bookings",
                        value = bookings.size.toString(),
                        icon = Icons.Default.Bookmark,
                        color = tertiaryColor
                    ),
                    StatCardData(
                        title = "Rating",
                        value = String.format("%.1f", viewModel.creatorRating),
                        icon = Icons.Default.Star,
                        color = Color(0xFFFFB800)
                    )
                )
            ) { stat ->
                StatCard(stat = stat)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Upcoming Shoots
        Text(
            text = "Upcoming Shoots",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(upcomingShoots) { booking ->
                UpcomingShootCard(booking = booking)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Quick Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionButton(
                icon = Icons.Default.Add,
                label = "Add Portfolio",
                onClick = { viewModel.navigateToPortfolioUpload() }
            )
            QuickActionButton(
                icon = Icons.Default.CalendarToday,
                label = "Manage Calendar",
                onClick = { viewModel.navigateToCalendar() }
            )
            QuickActionButton(
                icon = Icons.Default.Payment,
                label = "Earnings",
                onClick = { viewModel.navigateToEarnings() }
            )
        }
    }
}

@Composable
fun StatCard(stat: StatCardData) {
    Card(
        modifier = Modifier
            .width(120.dp)
            .height(100.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                stat.icon,
                contentDescription = null,
                tint = stat.color
            )
            Text(
                text = stat.value,
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stat.title,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

data class StatCardData(
    val title: String,
    val value: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun UpcomingShootCard(booking: Booking) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = booking.eventType,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${booking.date} at ${booking.time}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "${booking.hours} hours • ₹${booking.price}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(
                onClick = { /* View details */ },
                modifier = Modifier.height(36.dp)
            ) {
                Text("View")
            }
        }
    }
}
