package com.example.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.PayoutMethod
import com.example.data.model.PayoutMethodStatus
import com.example.data.model.PayoutMethodType
import com.example.ui.viewmodel.FokalViewModel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagePayoutMethods(
    viewModel: FokalViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val payoutMethods by viewModel.payoutMethods.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingMethod by remember { mutableStateOf<PayoutMethod?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Payout Methods",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header
            PremiumHeader(
                title = "Payout Methods",
                subtitle = "Add and manage your payout methods",
                icon = Icons.Outlined.AccountBalance
            )
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(payoutMethods) { method ->
                    PayoutMethodCard(
                        method = method,
                        onEdit = { editingMethod = method },
                        onDelete = { viewModel.deletePayoutMethod(method.id) },
                        onSetDefault = { viewModel.setDefaultPayoutMethod(method.id) }
                    )
                }
                
                item {
                    AddPayoutMethodCard(
                        onClick = { showAddDialog = true }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Preferences",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PayoutNotificationSettings(viewModel = viewModel)
                }
            }
        }
    }
    
    // Add/Edit Dialog
    if (showAddDialog) {
        AddPayoutMethodDialog(
            onDismiss = { showAddDialog = false },
            onSave = { method ->
                viewModel.addPayoutMethod(method)
                showAddDialog = false
            }
        )
    }
    
    if (editingMethod != null) {
        EditPayoutMethodDialog(
            method = editingMethod!!,
            onDismiss = { editingMethod = null },
            onSave = { method ->
                viewModel.updatePayoutMethod(method)
                editingMethod = null
            }
        )
    }
}

@Composable
fun PayoutMethodCard(
    method: PayoutMethod,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = if (method.isDefault) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (method.isDefault) 4.dp else 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
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
                            .size(48.dp)
                            .background(
                                color = if (method.type == PayoutMethodType.BANK_ACCOUNT)
                                    Color(0xFF4CAF50).copy(alpha = 0.1f)
                                else
                                    Color(0xFF2196F3).copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (method.type == PayoutMethodType.BANK_ACCOUNT)
                                Icons.Outlined.AccountBalance
                            else
                                Icons.Outlined.QrCodeScanner,
                            contentDescription = null,
                            tint = if (method.type == PayoutMethodType.BANK_ACCOUNT)
                                Color(0xFF4CAF50)
                            else
                                Color(0xFF2196F3)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = method.accountHolderName,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (method.isDefault) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        "Default",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = when (method.type) {
                                PayoutMethodType.BANK_ACCOUNT -> 
                                    "${method.bankName} - ****${method.accountNumber?.takeLast(4)}"
                                PayoutMethodType.UPI -> 
                                    method.upiId ?: ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (method.type == PayoutMethodType.BANK_ACCOUNT) {
                            Text(
                                text = "IFSC: ${method.ifscCode}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                StatusBadge(status = method.status)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (!method.isDefault) {
                    TextButton(onClick = onSetDefault) {
                        Icon(
                            Icons.Outlined.StarBorder,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Set Default", style = MaterialTheme.typography.bodySmall)
                    }
                }
                
                TextButton(onClick = onEdit) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit", style = MaterialTheme.typography.bodySmall)
                }
                
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: PayoutMethodStatus) {
    val (color, text) = when (status) {
        PayoutMethodStatus.ACTIVE -> 
            Color(0xFF4CAF50) to "Verified"
        PayoutMethodStatus.PENDING_VERIFICATION -> 
            Color(0xFFFFB800) to "Pending"
        PayoutMethodStatus.REJECTED -> 
            Color(0xFFF44336) to "Rejected"
    }
    
    Box(
        modifier = Modifier
            .background(
                color = color.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
fun AddPayoutMethodCard(
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.AddCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Add New Payout Method",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPayoutMethodDialog(
    onDismiss: () -> Unit,
    onSave: (PayoutMethod) -> Unit
) {
    var methodType by remember { mutableStateOf(PayoutMethodType.BANK_ACCOUNT) }
    var accountHolderName by remember { mutableStateOf("") }
    var accountNumber by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var ifscCode by remember { mutableStateOf("") }
    var upiId by remember { mutableStateOf("") }
    var setAsDefault by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Add Payout Method",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Method Type Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = methodType == PayoutMethodType.BANK_ACCOUNT,
                        onClick = { methodType = PayoutMethodType.BANK_ACCOUNT },
                        label = { 
                            Text("Bank Account", style = MaterialTheme.typography.labelMedium)
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.AccountBalance,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = methodType == PayoutMethodType.UPI,
                        onClick = { methodType = PayoutMethodType.UPI },
                        label = { 
                            Text("UPI ID", style = MaterialTheme.typography.labelMedium)
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.QrCodeScanner,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                OutlinedTextField(
                    value = accountHolderName,
                    onValueChange = { accountHolderName = it },
                    label = { Text("Account Holder Name") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) }
                )
                
                if (methodType == PayoutMethodType.BANK_ACCOUNT) {
                    OutlinedTextField(
                        value = accountNumber,
                        onValueChange = { accountNumber = it },
                        label = { Text("Account Number") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Outlined.Numbers, contentDescription = null) }
                    )
                    OutlinedTextField(
                        value = bankName,
                        onValueChange = { bankName = it },
                        label = { Text("Bank Name") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Outlined.Business, contentDescription = null) }
                    )
                    OutlinedTextField(
                        value = ifscCode,
                        onValueChange = { ifscCode = it },
                        label = { Text("IFSC Code") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Outlined.Code, contentDescription = null) }
                    )
                } else {
                    OutlinedTextField(
                        value = upiId,
                        onValueChange = { upiId = it },
                        label = { Text("UPI ID (e.g., name@upi)") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Outlined.QrCodeScanner, contentDescription = null) }
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Set as default",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = setAsDefault,
                        onCheckedChange = { setAsDefault = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val method = PayoutMethod(
                        id = UUID.randomUUID().toString(),
                        userId = "", // Will be set by ViewModel
                        type = methodType,
                        accountHolderName = accountHolderName,
                        accountNumber = if (methodType == PayoutMethodType.BANK_ACCOUNT) accountNumber else null,
                        bankName = if (methodType == PayoutMethodType.BANK_ACCOUNT) bankName else null,
                        ifscCode = if (methodType == PayoutMethodType.BANK_ACCOUNT) ifscCode else null,
                        upiId = if (methodType == PayoutMethodType.UPI) upiId else null,
                        isDefault = setAsDefault,
                        status = PayoutMethodStatus.PENDING_VERIFICATION,
                        createdAt = ""
                    )
                    onSave(method)
                },
                enabled = accountHolderName.isNotBlank() && 
                    ((methodType == PayoutMethodType.BANK_ACCOUNT && 
                        accountNumber.isNotBlank() && 
                        bankName.isNotBlank() && 
                        ifscCode.isNotBlank()) ||
                     (methodType == PayoutMethodType.UPI && upiId.isNotBlank()))
            ) {
                Text("Add Method")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPayoutMethodDialog(
    method: PayoutMethod,
    onDismiss: () -> Unit,
    onSave: (PayoutMethod) -> Unit
) {
    var accountHolderName by remember { mutableStateOf(method.accountHolderName) }
    var accountNumber by remember { mutableStateOf(method.accountNumber ?: "") }
    var bankName by remember { mutableStateOf(method.bankName ?: "") }
    var ifscCode by remember { mutableStateOf(method.ifscCode ?: "") }
    var upiId by remember { mutableStateOf(method.upiId ?: "") }
    var setAsDefault by remember { mutableStateOf(method.isDefault) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Edit Payout Method",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Type: ${if (method.type == PayoutMethodType.BANK_ACCOUNT) "Bank Account" else "UPI ID"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                
                OutlinedTextField(
                    value = accountHolderName,
                    onValueChange = { accountHolderName = it },
                    label = { Text("Account Holder Name") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) }
                )
                
                if (method.type == PayoutMethodType.BANK_ACCOUNT) {
                    OutlinedTextField(
                        value = accountNumber,
                        onValueChange = { accountNumber = it },
                        label = { Text("Account Number") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Outlined.Numbers, contentDescription = null) }
                    )
                    OutlinedTextField(
                        value = bankName,
                        onValueChange = { bankName = it },
                        label = { Text("Bank Name") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Outlined.Business, contentDescription = null) }
                    )
                    OutlinedTextField(
                        value = ifscCode,
                        onValueChange = { ifscCode = it },
                        label = { Text("IFSC Code") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Outlined.Code, contentDescription = null) }
                    )
                } else {
                    OutlinedTextField(
                        value = upiId,
                        onValueChange = { upiId = it },
                        label = { Text("UPI ID (e.g., name@upi)") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Outlined.QrCodeScanner, contentDescription = null) }
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Set as default",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = setAsDefault,
                        onCheckedChange = { setAsDefault = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = method.copy(
                        accountHolderName = accountHolderName,
                        accountNumber = if (method.type == PayoutMethodType.BANK_ACCOUNT) accountNumber else null,
                        bankName = if (method.type == PayoutMethodType.BANK_ACCOUNT) bankName else null,
                        ifscCode = if (method.type == PayoutMethodType.BANK_ACCOUNT) ifscCode else null,
                        upiId = if (method.type == PayoutMethodType.UPI) upiId else null,
                        isDefault = setAsDefault
                    )
                    onSave(updated)
                },
                enabled = accountHolderName.isNotBlank() && 
                    ((method.type == PayoutMethodType.BANK_ACCOUNT && 
                        accountNumber.isNotBlank() && 
                        bankName.isNotBlank() && 
                        ifscCode.isNotBlank()) ||
                     (method.type == PayoutMethodType.UPI && upiId.isNotBlank()))
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun PremiumHeader(
    title: String,
    subtitle: String,
    icon: ImageVector
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}
