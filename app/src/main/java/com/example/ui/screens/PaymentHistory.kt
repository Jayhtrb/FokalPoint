package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.Payment
import com.example.ui.viewmodel.FokalViewModel

@Composable
fun PaymentHistoryScreen(
    viewModel: FokalViewModel = viewModel()
) {
    val payments by viewModel.paymentHistory.collectAsState()
    var filterType by remember { mutableStateOf(FilterType.All) }
    var searchQuery by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "Payment History",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Search and Filter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search payments...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )
            
            FilterDropdown(
                options = FilterType.values(),
                selected = filterType,
                onSelect = { filterType = it }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Summary Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PaymentSummaryCard(
                title = "Total Paid",
                amount = payments.sumOf { it.amount },
                icon = Icons.Default.Payment
            )
            PaymentSummaryCard(
                title = "Pending",
                amount = payments.filter { it.status == "pending" }.sumOf { it.amount },
                icon = Icons.Default.HourglassEmpty,
                color = Color(0xFFFFB800)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Payment List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                payments.filter { payment ->
                    when (filterType) {
                        FilterType.All -> true
                        FilterType.Booking -> payment.type == "booking"
                        FilterType.Payout -> payment.type == "payout"
                        FilterType.Refund -> payment.type == "refund"
                    }
                }.filter { payment ->
                    payment.description.contains(searchQuery, ignoreCase = true) ||
                    payment.transactionId.contains(searchQuery, ignoreCase = true)
                }
            ) { payment ->
                PaymentItem(
                    payment = payment,
                    onViewDetails = { viewModel.navigateToPaymentDetails(payment.id) },
                    onDownloadInvoice = { viewModel.downloadInvoice(payment.id) }
                )
            }
        }
    }
}

@Composable
fun RowScope.PaymentSummaryCard(
    title: String,
    amount: Double,
    icon: ImageVector,
    color: Color = MaterialTheme.colorScheme.primary
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
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color
            )
            Column {
                Text(
                    text = "₹${String.format("%.2f", amount)}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PaymentItem(
    payment: Payment,
    onViewDetails: () -> Unit,
    onDownloadInvoice: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onViewDetails)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = when (payment.type) {
                                    "booking" -> Color.Green.copy(alpha = 0.1f)
                                    "payout" -> Color(0xFFFFB800).copy(alpha = 0.1f)
                                    "refund" -> Color.Red.copy(alpha = 0.1f)
                                    else -> Color.Gray.copy(alpha = 0.1f)
                                },
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            when (payment.type) {
                                "booking" -> Icons.Default.CheckCircle
                                "payout" -> Icons.Default.ArrowUpward
                                "refund" -> Icons.Default.Refresh
                                else -> Icons.Default.Payment
                            },
                            contentDescription = null,
                            tint = when (payment.type) {
                                "booking" -> Color.Green
                                "payout" -> Color(0xFFFFB800)
                                "refund" -> Color.Red
                                else -> Color.Gray
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column {
                        Text(
                            text = payment.description,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "${payment.date} • ${payment.transactionId}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (payment.amount > 0) 
                            "₹${String.format("%.2f", payment.amount)}" 
                        else 
                            "-₹${String.format("%.2f", -payment.amount)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (payment.amount > 0) Color.Green else Color.Red
                    )
                    Row {
                        Badge(
                            containerColor = when (payment.status) {
                                "completed" -> Color.Green
                                "pending" -> Color(0xFFFFB800)
                                "failed" -> Color.Red
                                else -> Color.Gray
                            }
                        ) {
                            Text(
                                payment.status,
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDownloadInvoice) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Invoice", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun FilterDropdown(
    options: Array<FilterType>,
    selected: FilterType,
    onSelect: (FilterType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.height(56.dp)
        ) {
            Text(selected.label)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                    leadingIcon = if (option == selected) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null
                )
            }
        }
    }
}

enum class FilterType(val label: String) {
    All("All"),
    Booking("Bookings"),
    Payout("Payouts"),
    Refund("Refunds")
}
