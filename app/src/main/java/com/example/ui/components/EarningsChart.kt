package com.example.ui.components

import android.graphics.drawable.GradientDrawable
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.model.EarningsDataPoint
import com.example.data.model.PayoutPeriod
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet

// Extension properties to bridge the user's requested API properties
val PayoutPeriod.label: String
    get() = this.name

val EarningsDataPoint.label: String
    get() = this.date

@Composable
fun PremiumEarningsChart(
    earningsData: List<EarningsDataPoint>,
    period: PayoutPeriod,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        AndroidView(
            factory = { context ->
                LineChart(context).apply {
                    description.isEnabled = false
                    setTouchEnabled(true)
                    isDragEnabled = true
                    setScaleEnabled(true)
                    setPinchZoom(true)
                    
                    // Styling
                    setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
                    axisLeft.textColor = ContextCompat.getColor(context, android.R.color.darker_gray)
                    xAxis.textColor = ContextCompat.getColor(context, android.R.color.darker_gray)
                    
                    // Grid
                    axisLeft.setDrawGridLines(true)
                    xAxis.setDrawGridLines(false)
                    
                    // Legend
                    legend.isEnabled = false
                    
                    // Data
                    val entries = earningsData.mapIndexed { index, data ->
                        Entry(index.toFloat(), data.amount.toFloat())
                    }
                    
                    val dataSet = LineDataSet(entries, "Earnings").apply {
                        color = ContextCompat.getColor(context, R.color.purple_500)
                        setCircleColor(ContextCompat.getColor(context, R.color.purple_500))
                        lineWidth = 3f
                        circleRadius = 4f
                        setDrawCircleHole(true)
                        valueTextColor = ContextCompat.getColor(context, android.R.color.darker_gray)
                        valueTextSize = 10f
                        
                        // Gradient fill
                        setDrawFilled(true)
                        fillDrawable = GradientDrawable().apply {
                            orientation = GradientDrawable.Orientation.TOP_BOTTOM
                            colors = intArrayOf(
                                ContextCompat.getColor(context, R.color.purple_500),
                                ContextCompat.getColor(context, android.R.color.transparent)
                            )
                        }
                    }
                    
                    this.data = LineData(dataSet)
                    invalidate()
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun CustomEarningsChart(
    data: List<EarningsDataPoint>,
    period: PayoutPeriod,
    modifier: Modifier = Modifier
) {
    val maxAmount = data.maxOfOrNull { it.amount } ?: 1.0
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Earnings Overview",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "${period.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Chart bars
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                data.forEach { point ->
                    val height = ((point.amount / maxAmount) * 100).coerceAtLeast(5.0)
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "₹${point.amount.toInt()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .height(height.dp)
                                .background(
                                    color = if (point.amount == maxAmount)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                                )
                                .animateContentSize()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            point.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
