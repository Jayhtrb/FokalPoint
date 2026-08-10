package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.FokalViewModel
import com.example.ui.viewmodel.UPIApp

@Composable
fun UPIPaymentScreen(
    bookingId: Long,
    creatorId: String,
    amount: Double,
    onPaymentComplete: (Boolean) -> Unit,
    viewModel: FokalViewModel = hiltViewModel()
) {
    // Load creator profile
    LaunchedEffect(creatorId) {
        viewModel.loadCreatorProfile(creatorId)
    }

    val creator by viewModel.creatorProfile.collectAsState()
    var selectedUPI by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var customUPIId by remember { mutableStateOf("") }
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // Header
        PremiumHeader(
            title = "Pay with UPI",
            subtitle = "Pay directly to ${creator?.name ?: "Creator"}",
            icon = Icons.Outlined.QrCodeScanner
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Amount Display
        GlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Amount to Pay",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "₹${String.format("%.2f", amount)}",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Booking #${bookingId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // UPI Methods
        Text(
            text = "Select UPI App",
            style = MaterialTheme.typography.titleMedium
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(creator?.upiMethods ?: emptyList()) { upiMethod ->
                UPIAppCard(
                    upiMethod = upiMethod,
                    isSelected = selectedUPI == upiMethod.id,
                    onClick = { 
                        selectedUPI = upiMethod.id
                        customUPIId = ""
                    }
                )
            }
            
            item {
                // Custom UPI entry
                OutlinedTextField(
                    value = customUPIId,
                    onValueChange = { 
                        customUPIId = it
                        selectedUPI = "custom"
                    },
                    placeholder = { Text("Enter UPI ID (e.g., name@upi)") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    singleLine = true
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Pay Button
        val upiToUse = if (selectedUPI == "custom") customUPIId else creator?.upiMethods?.find { it.id == selectedUPI }?.upiId ?: ""
        val isPayEnabled = (selectedUPI != null && selectedUPI != "custom") || (selectedUPI == "custom" && customUPIId.contains("@"))

        Button(
            onClick = {
                isProcessing = true
                // Trigger UPI payment intent
                val upiIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("upi://pay?pa=${upiToUse}&pn=${creator?.name ?: "Fokal"}&am=${amount}&cu=INR&tn=Booking $bookingId")
                }
                try {
                    context.startActivity(Intent.createChooser(upiIntent, "Pay with UPI"))
                } catch (e: Exception) {
                    android.util.Log.e("UPIPayment", "No UPI apps found: ${e.message}")
                }
                
                // Monitor for success (simplified - actual implementation needs deep link)
                viewModel.monitorUPIPayment(bookingId) { success ->
                    isProcessing = false
                    onPaymentComplete(success)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = isPayEnabled && !isProcessing
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White
                )
            } else {
                Text("Pay ₹${String.format("%.2f", amount)}")
            }
        }
    }
}

@Composable
fun UPIAppCard(
    upiMethod: UPIApp,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected)
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // UPI App Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = upiMethod.color.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    upiMethod.icon,
                    contentDescription = null,
                    tint = upiMethod.color
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Text(
                    text = upiMethod.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = upiMethod.upiId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            if (isSelected) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}


@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
    ) {
        content()
    }
}
