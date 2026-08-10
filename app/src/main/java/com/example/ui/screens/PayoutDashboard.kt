package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.components.CustomEarningsChart
import com.example.ui.components.PremiumEarningsChart
import com.example.ui.viewmodel.FokalViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayoutDashboard(
    viewModel: FokalViewModel = hiltViewModel()
) {
    val payoutStats by viewModel.payoutStats.collectAsState()
    val pendingPayouts by viewModel.pendingPayouts.collectAsState()
    val payoutHistory by viewModel.payoutHistory.collectAsState()
    var selectedPeriod by remember { mutableStateOf(PayoutPeriod.Monthly) }
    var chartStyle by remember { mutableStateOf("Premium") } // "Premium", "Canvas", "Bar"

    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Payout Dashboard") },
                actions = {
                    IconButton(onClick = { /* Refresh */ }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Stats Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PayoutStatCard(
                    title = "Available",
                    amount = payoutStats.available,
                    icon = Icons.Default.AccountBalance,
                    color = MaterialTheme.colorScheme.primary
                )
                PayoutStatCard(
                    title = "Pending",
                    amount = payoutStats.pending,
                    icon = Icons.Default.HourglassEmpty,
                    color = Color(0xFFFFB800)
                )
                PayoutStatCard(
                    title = "Total Earned",
                    amount = payoutStats.totalEarned,
                    icon = Icons.Default.TrendingUp,
                    color = Color(0xFF4CAF50)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Period Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PayoutPeriodButton(
                    label = "Daily",
                    selected = selectedPeriod == PayoutPeriod.Daily,
                    onClick = { selectedPeriod = PayoutPeriod.Daily }
                )
                PayoutPeriodButton(
                    label = "Weekly",
                    selected = selectedPeriod == PayoutPeriod.Weekly,
                    onClick = { selectedPeriod = PayoutPeriod.Weekly }
                )
                PayoutPeriodButton(
                    label = "Monthly",
                    selected = selectedPeriod == PayoutPeriod.Monthly,
                    onClick = { selectedPeriod = PayoutPeriod.Monthly }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Earnings Chart
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Earnings Overview",
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        // Style selectors
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("Premium", "Canvas", "Bar").forEach { style ->
                                val isSelected = chartStyle == style
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { chartStyle = style },
                                    label = { Text(style, fontSize = 10.sp) },
                                    modifier = Modifier.height(28.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    when (chartStyle) {
                        "Premium" -> {
                            PremiumEarningsChart(
                                earningsData = payoutStats.earningsData,
                                period = selectedPeriod,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        "Bar" -> {
                            CustomEarningsChart(
                                data = payoutStats.earningsData,
                                period = selectedPeriod,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        else -> {
                            EarningsChart(
                                data = payoutStats.earningsData,
                                period = selectedPeriod
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Pending Payouts
            if (pendingPayouts.isNotEmpty()) {
                Text(
                    text = "Pending Payouts",
                    style = MaterialTheme.typography.titleMedium
                )
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pendingPayouts) { payout ->
                        PendingPayoutItem(
                            payout = payout,
                            onRequestPayout = { viewModel.requestPayout(payout.id) }
                        )
                    }
                }
            }
            
            // Payout History
            Text(
                text = "Payout History",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            LazyColumn(
                modifier = Modifier
                    .weight(if (pendingPayouts.isNotEmpty()) 0.5f else 1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(payoutHistory) { payout ->
                    PayoutHistoryItem(payout = payout)
                }
            }
            
            // Request Payout Button
            Button(
                onClick = { viewModel.requestPayout() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .height(56.dp),
                enabled = payoutStats.available > 0
            ) {
                Icon(Icons.Default.AttachMoney, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Request Payout - ₹${String.format("%.2f", payoutStats.available)}")
            }
        }
    }
}

@Composable
fun RowScope.PayoutStatCard(
    title: String,
    amount: Double,
    icon: ImageVector,
    color: Color
) {
    Card(
        modifier = Modifier
            .weight(1f)
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
            Icon(icon, contentDescription = null, tint = color)
            Column {
                Text(
                    text = "₹${String.format("%.2f", amount)}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun PendingPayoutItem(
    payout: PendingPayout,
    onRequestPayout: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Booking #${payout.bookingId}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${payout.eventType} - ${payout.date}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "₹${String.format("%.2f", payout.amount)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = payout.status,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFFB800)
                )
            }
        }
    }
}

@Composable
fun PayoutHistoryItem(payout: PayoutHistory) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (payout.status == "Completed") 
                            Color.Green.copy(alpha = 0.1f) 
                        else 
                            Color.Red.copy(alpha = 0.1f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (payout.status == "Completed") 
                        Icons.Default.CheckCircle 
                    else 
                        Icons.Default.Cancel,
                    contentDescription = null,
                    tint = if (payout.status == "Completed") Color.Green else Color.Red
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = payout.description,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${payout.date} • ${payout.method}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "₹${String.format("%.2f", payout.amount)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (payout.amount > 0) Color.Green else Color.Red
                )
                Text(
                    text = payout.status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (payout.status == "Completed") 
                        Color.Green 
                    else 
                        Color.Red
                )
            }
        }
    }
}

@Composable
fun PayoutPeriodButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    if (selected) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier.height(36.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.height(36.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun EarningsChart(
    data: List<EarningsDataPoint>,
    period: PayoutPeriod,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No earnings data available for this period",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(top = 16.dp, bottom = 12.dp, start = 16.dp, end = 16.dp)
        ) {
            val width = size.width
            val height = size.height
            val maxAmount = data.maxOfOrNull { it.amount }?.coerceAtLeast(1.0) ?: 1.0

            val stepX = width / (data.size - 1).coerceAtLeast(1)
            val points = data.mapIndexed { index, point ->
                val x = index * stepX
                val y = height - (point.amount / maxAmount * height).toFloat()
                androidx.compose.ui.geometry.Offset(x, y)
            }

            // Draw background grid lines
            val gridLines = 4
            for (i in 0..gridLines) {
                val y = height * i / gridLines
                drawLine(
                    color = labelColor.copy(alpha = 0.1f),
                    start = androidx.compose.ui.geometry.Offset(0f, y),
                    end = androidx.compose.ui.geometry.Offset(width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Draw chart line
            if (points.isNotEmpty()) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        lineTo(points[i].x, points[i].y)
                    }
                }

                // Filled gradient under line
                val fillPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(points.first().x, height)
                    for (point in points) {
                        lineTo(point.x, point.y)
                    }
                    lineTo(points.last().x, height)
                    close()
                }

                drawPath(
                    path = fillPath,
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(primaryColor.copy(alpha = 0.3f), Color.Transparent),
                        startY = 0f,
                        endY = height
                    )
                )

                drawPath(
                    path = path,
                    color = primaryColor,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 3.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )

                // Draw data points and value labels
                points.forEachIndexed { index, point ->
                    drawCircle(
                        color = secondaryColor,
                        radius = 5.dp.toPx(),
                        center = point
                    )
                    drawCircle(
                        color = primaryColor,
                        radius = 3.dp.toPx(),
                        center = point
                    )
                }
            }
        }

        // Horizontal Labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            data.forEach { point ->
                Text(
                    text = point.date,
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor
                )
            }
        }
    }
}
