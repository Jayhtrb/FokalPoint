package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.FokalViewModel

@Composable
fun PayoutNotificationSettings(
    viewModel: FokalViewModel = hiltViewModel()
) {
    val notificationPreferences by viewModel.notificationPreferences.collectAsState()
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Outlined.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Payout Notifications",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Get updates about your earnings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = notificationPreferences.payoutNotifications,
                    onCheckedChange = { 
                        viewModel.updateNotificationPreference("payout", it)
                    }
                )
            }
            
            if (notificationPreferences.payoutNotifications) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NotificationToggle(
                        label = "Payout Processed",
                        checked = notificationPreferences.payoutProcessed,
                        onCheckedChange = { 
                            viewModel.updateNotificationPreference("payout_processed", it)
                        }
                    )
                    NotificationToggle(
                        label = "Payout Failed",
                        checked = notificationPreferences.payoutFailed,
                        onCheckedChange = { 
                            viewModel.updateNotificationPreference("payout_failed", it)
                        }
                    )
                    NotificationToggle(
                        label = "Weekly Earnings Report",
                        checked = notificationPreferences.weeklyReport,
                        onCheckedChange = { 
                            viewModel.updateNotificationPreference("weekly_report", it)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.8f)
        )
    }
}
