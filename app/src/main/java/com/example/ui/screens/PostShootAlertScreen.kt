@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.service.LocationService
import com.example.data.service.LocationService.CityInfo
import com.example.ui.components.PremiumHeader
import com.example.ui.theme.AmberGold
import com.example.ui.viewmodel.FokalViewModel

@Composable
fun PostShootAlertScreen(
    viewModel: FokalViewModel,
    onBack: () -> Unit
) {
    var eventType by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var selectedCity by remember { mutableStateOf<CityInfo?>(null) }
    var searchRadius by remember { mutableStateOf(50) }
    var budget by remember { mutableStateOf("") }
    var timeframe by remember { mutableStateOf("") }
    var shootDescription by remember { mutableStateOf("") }
    var additionalDetails by remember { mutableStateOf("") }
    var showCitySearch by remember { mutableStateOf(false) }
    var isDetectingLocation by remember { mutableStateOf(false) }
    var showCreatorSearch by remember { mutableStateOf(false) }
    val referenceImages = remember { mutableStateListOf<String>() }
    
    val context = LocalContext.current
    val locationService = remember { LocationService(context) }
    val cities = remember { locationService.majorCities }
    val nearbyCities = remember(selectedCity, searchRadius) {
        selectedCity?.let { locationService.getCitiesWithinRadius(it, searchRadius) } ?: emptyList()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Post Shoot Alert",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Search Button
                    IconButton(
                        onClick = { showCreatorSearch = true }
                    ) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = "Search Creators"
                        )
                    }
                }
            )
        },
        modifier = Modifier.testTag("post_shoot_alert_scaffold")
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Progress Steps
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 4.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                item {
                    PremiumHeader(
                        title = "Post Worldwide Shoot Alert",
                        subtitle = "Find the perfect photographer anywhere in the world",
                        icon = Icons.Outlined.Public
                    )
                }
                
                // Location Detection
                item {
                    LocationDetectionCard(
                        isDetecting = isDetectingLocation,
                        selectedCity = selectedCity,
                        onDetectLocation = {
                            isDetectingLocation = true
                            viewModel.detectUserCity { city ->
                                selectedCity = city
                                isDetectingLocation = false
                                if (city != null) {
                                    location = "${city.name}, ${city.state}"
                                }
                            }
                        }
                    )
                }
                
                // Event Type
                item {
                    EventTypeSection(
                        eventType = eventType,
                        onEventTypeChange = { eventType = it }
                    )
                }
                
                // Shooting Location with Radius
                item {
                    LocationSection(
                        location = location,
                        onLocationChange = { location = it },
                        selectedCity = selectedCity,
                        searchRadius = searchRadius,
                        onRadiusChange = { searchRadius = it },
                        nearbyCities = nearbyCities,
                        onShowCitySearch = { showCitySearch = true }
                    )
                }
                
                // Budget & Timeframe
                item {
                    BudgetTimeframeSection(
                        budget = budget,
                        onBudgetChange = { budget = it },
                        timeframe = timeframe,
                        onTimeframeChange = { timeframe = it }
                    )
                }
                
                // Shoot Description
                item {
                    ShootDescriptionSection(
                        description = shootDescription,
                        onDescriptionChange = { shootDescription = it },
                        referenceImages = referenceImages,
                        onAddImage = { referenceImages.add(it) },
                        onRemoveImage = { referenceImages.removeAt(it) }
                    )
                }
                
                // Additional Details (For Photographers)
                item {
                    AdditionalDetailsSection(
                        additionalDetails = additionalDetails,
                        onAdditionalDetailsChange = { additionalDetails = it }
                    )
                }
                
                // Nearby Creators Preview
                if (selectedCity != null) {
                    item {
                        NearbyCreatorsPreview(
                            city = selectedCity!!,
                            radius = searchRadius,
                            onViewAll = { showCreatorSearch = true }
                        )
                    }
                }
                
                // Action Buttons
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onBack,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                val referenceImagesString = if (referenceImages.isNotEmpty()) {
                                    referenceImages.joinToString(",")
                                } else null
                                viewModel.postShootAlert(
                                    eventType = eventType,
                                    location = location,
                                    budget = budget.toDoubleOrNull() ?: 0.0,
                                    timeframe = timeframe,
                                    description = shootDescription,
                                    additionalDetails = additionalDetails,
                                    cityId = selectedCity?.name ?: "",
                                    referenceImages = referenceImagesString
                                )
                                onBack()
                            },
                            modifier = Modifier.weight(2f).testTag("post_alert_action_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AmberGold,
                                contentColor = Color.Black
                            ),
                            enabled = eventType.isNotBlank() && 
                                    location.isNotBlank() && 
                                    budget.isNotBlank()
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.Send,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Post Alert", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
    
    // City Search Dialog
    if (showCitySearch) {
        CitySearchDialog(
            cities = cities,
            onCitySelected = { city ->
                selectedCity = city
                location = "${city.name}, ${city.state}"
                showCitySearch = false
            },
            onDismiss = { showCitySearch = false }
        )
    }
    
    // Creator Search Dialog
    if (showCreatorSearch) {
        CreatorSearchDialog(
            selectedCity = selectedCity,
            radius = searchRadius,
            onDismiss = { showCreatorSearch = false },
            onCreatorSelected = { /* Navigate to profile */ }
        )
    }
}

@Composable
fun LocationDetectionCard(
    isDetecting: Boolean,
    selectedCity: CityInfo?,
    onDetectLocation: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("location_detection_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Your Location",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (selectedCity != null) {
                        Text(
                            "${selectedCity.name}, ${selectedCity.state}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Button(
                    onClick = onDetectLocation,
                    enabled = !isDetecting,
                    modifier = Modifier.height(36.dp)
                ) {
                    if (isDetecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Outlined.GpsFixed,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Detect")
                    }
                }
            }
            
            if (selectedCity != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Tag(text = "📍 ${selectedCity.name}")
                    Tag(text = "📡 ${selectedCity.radius}km radius")
                    Tag(text = "🌍 ${selectedCity.country}")
                }
            }
        }
    }
}

@Composable
fun LocationSection(
    location: String,
    onLocationChange: (String) -> Unit,
    selectedCity: CityInfo?,
    searchRadius: Int,
    onRadiusChange: (Int) -> Unit,
    nearbyCities: List<CityInfo>,
    onShowCitySearch: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "Shooting Location & Search Radius",
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Location Input with Search
            OutlinedTextField(
                value = location,
                onValueChange = onLocationChange,
                label = { Text("Location") },
                placeholder = { Text("e.g., Hyderabad, Telangana") },
                leadingIcon = { Icon(Icons.Outlined.LocationOn, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = onShowCitySearch) {
                        Icon(Icons.Outlined.Search, contentDescription = "Search")
                    }
                },
                singleLine = true,
                readOnly = true
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Radius Slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Search Radius",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "$searchRadius km",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Slider(
                    value = searchRadius.toFloat(),
                    onValueChange = { onRadiusChange(it.toInt()) },
                    valueRange = 5f..100f,
                    steps = 18,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
            
            if (nearbyCities.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Nearby Cities (within $searchRadius km)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    nearbyCities.take(5).forEach { city ->
                        AssistChip(
                            onClick = { /* Filter by city */ },
                            label = { 
                                Text(
                                    "${city.name} (${city.state})",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                    if (nearbyCities.size > 5) {
                        AssistChip(
                            onClick = { /* Show all */ },
                            label = { 
                                Text(
                                    "+${nearbyCities.size - 5} more",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EventTypeSection(
    eventType: String,
    onEventTypeChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "Event / Shoot Type",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "What type of shoot do you need?",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            val eventTypes = listOf(
                "Wedding", "Birthday", "Baby Shoot", "Maternity",
                "Corporate", "Fashion", "Pre-Wedding", "Anniversary",
                "Portrait", "Events", "Travel", "Food", "Lifestyle",
                "Graduation", "Newborn", "Kids", "Pets"
            )
            
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                eventTypes.forEach { type ->
                    FilterChip(
                        selected = eventType == type,
                        onClick = { onEventTypeChange(if (eventType == type) "" else type) },
                        label = { 
                            Text(
                                type,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun BudgetTimeframeSection(
    budget: String,
    onBudgetChange: (String) -> Unit,
    timeframe: String,
    onTimeframeChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "Budget & Timeline",
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Budget
            OutlinedTextField(
                value = budget,
                onValueChange = { 
                    if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                        onBudgetChange(it)
                    }
                },
                label = { Text("Budget (₹)") },
                placeholder = { Text("e.g., 25000") },
                leadingIcon = { Icon(Icons.Outlined.Payment, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Timeframe
            OutlinedTextField(
                value = timeframe,
                onValueChange = onTimeframeChange,
                label = { Text("Timeframe") },
                placeholder = { Text("e.g., Next Month, This Weekend") },
                leadingIcon = { Icon(Icons.Outlined.CalendarToday, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

@Composable
fun ShootDescriptionSection(
    description: String,
    onDescriptionChange: (String) -> Unit,
    referenceImages: List<String>,
    onAddImage: (String) -> Unit,
    onRemoveImage: (Int) -> Unit
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            val path = saveBitmapToCache(context, bitmap)
            if (path != null) {
                onAddImage(path)
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            onAddImage(uri.toString())
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch(null)
        } else {
            android.widget.Toast.makeText(context, "Camera permission is required to take reference photos", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "Shoot Description / Brief",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Describe your vision, requirements, and expectations",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = description,
                onValueChange = onDescriptionChange,
                placeholder = { 
                    Text("Describe your shoot requirements in detail...") 
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                maxLines = 5
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Reference Images Section
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        "Reference Vision Board",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "Add up to 3 camera or gallery photos (${referenceImages.size}/3)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (referenceImages.size < 3) {
                    FilledTonalButton(
                        onClick = { showDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("add_reference_image_button")
                    ) {
                        Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (referenceImages.isEmpty()) {
                // Empty state for images
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { showDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Outlined.PhotoCamera,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Tap to add a reference photo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                // Horizontal list of images
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(referenceImages.size) { index ->
                        val imagePath = referenceImages[index]
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            coil.compose.AsyncImage(
                                model = imagePath,
                                contentDescription = "Reference photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                            
                            // Delete button overlay
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(24.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    .clickable { onRemoveImage(index) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = "Remove image",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Add Reference Image") },
            text = {
                Text("Capture a picture or choose a photo from your gallery to express your creative vision.")
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            showDialog = false
                            val hasCameraPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                context, android.Manifest.permission.CAMERA
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                            if (hasCameraPermission) {
                                cameraLauncher.launch(null)
                            } else {
                                permissionLauncher.launch(android.Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("add_image_camera_option")
                    ) {
                        Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Take Photo (Camera)")
                    }

                    OutlinedButton(
                        onClick = {
                            showDialog = false
                            galleryLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth().testTag("add_image_gallery_option")
                    ) {
                        Icon(Icons.Outlined.Photo, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Choose from Gallery")
                    }

                    ElevatedButton(
                        onClick = {
                            showDialog = false
                            val sampleUrls = listOf(
                                "https://images.unsplash.com/photo-1511285560929-80b456fea0bc?q=80&w=300",
                                "https://images.unsplash.com/photo-1519741497674-611481863552?q=80&w=300",
                                "https://images.unsplash.com/photo-1520854221256-17451cc331bf?q=80&w=300"
                            )
                            val availableSample = sampleUrls.firstOrNull { it !in referenceImages } ?: sampleUrls.random()
                            onAddImage(availableSample)
                        },
                        modifier = Modifier.fillMaxWidth().testTag("add_image_sample_option"),
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Sample Photo")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AdditionalDetailsSection(
    additionalDetails: String,
    onAdditionalDetailsChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "📸 For Photographers: Additional Details",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Text(
                "Share your unique strengths, equipment, style, or any special offerings",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = additionalDetails,
                onValueChange = onAdditionalDetailsChange,
                placeholder = { 
                    Text("""
                        Examples:
                        • Specializing in candid wedding photography with 5+ years experience
                        • Equipment: Sony A7IV, 24-70mm GM, 70-200mm GM
                        • Additional services: Drone coverage, same-day edits
                        • Awards: Best Wedding Photographer 2024
                    """.trimIndent())
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                maxLines = 6
            )
        }
    }
}

@Composable
fun NearbyCreatorsPreview(
    city: CityInfo,
    radius: Int,
    onViewAll: () -> Unit
) {
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
                Column {
                    Text(
                        "Nearby Creators",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Within $radius km radius in ${city.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onViewAll) {
                    Text("View All")
                    Icon(Icons.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Sample Creator Previews
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(5) { index ->
                    CreatorPreviewCard(
                        creator = CreatorPreview(
                            name = if (index == 0) "Amit Sharma" else if (index == 1) "Riya Sen" else "Kabir Singh",
                            specialty = "Wedding & Portrait",
                            rating = 4.8,
                            distance = "${12 + index * 3}km",
                            price = "₹25,000",
                            image = null
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun CreatorPreviewCard(
    creator: CreatorPreview
) {
    Card(
        modifier = Modifier
            .width(160.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                creator.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1
            )
            Text(
                creator.specialty,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Star,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = Color(0xFFFFB800)
                    )
                    Text(
                        creator.rating.toString(),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    creator.distance,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "From ${creator.price}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun CitySearchDialog(
    cities: List<CityInfo>,
    onCitySelected: (CityInfo) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "Select Location",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search cities...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyColumn(
                    modifier = Modifier
                        .height(300.dp)
                        .fillMaxWidth()
                ) {
                    items(
                        cities.filter { city ->
                            city.name.contains(searchQuery, ignoreCase = true) ||
                            city.state.contains(searchQuery, ignoreCase = true)
                        }
                    ) { city ->
                        TextButton(
                            onClick = { onCitySelected(city) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "${city.name}, ${city.state}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "${city.country}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CreatorSearchDialog(
    selectedCity: CityInfo?,
    radius: Int,
    onDismiss: () -> Unit,
    onCreatorSelected: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterByRating by remember { mutableStateOf(false) }
    var filterByPrice by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "Find Creators Near You",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by name, specialty...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Filters
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filterByRating,
                        onClick = { filterByRating = !filterByRating },
                        label = { Text("⭐ 4.5+") }
                    )
                    FilterChip(
                        selected = filterByPrice,
                        onClick = { filterByPrice = !filterByPrice },
                        label = { Text("💰 Under ₹50K") }
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "Showing creators within ${radius}km of ${selectedCity?.name ?: "your location"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Creator List
                LazyColumn(
                    modifier = Modifier.height(300.dp)
                ) {
                    items(10) { index ->
                        CreatorSearchItem(
                            creator = CreatorSearch(
                                name = if (index % 3 == 0) "Amit Sharma" else if (index % 3 == 1) "Riya Sen" else "Kabir Singh",
                                specialty = "Wedding Photographer",
                                rating = 4.8,
                                reviews = 127 + index,
                                price = "₹25,000",
                                distance = "${12 + index * 3}km",
                                verified = index % 2 == 0
                            ),
                            onClick = onCreatorSelected
                        )
                        if (index < 9) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun CreatorSearchItem(
    creator: CreatorSearch,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                creator.name.first().toString(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    creator.name,
                    style = MaterialTheme.typography.titleMedium
                )
                if (creator.verified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Outlined.Verified,
                        contentDescription = "Verified",
                        modifier = Modifier.size(14.dp),
                        tint = Color(0xFF1DA1F2)
                    )
                }
            }
            Text(
                creator.specialty,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Star,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = Color(0xFFFFB800)
                )
                Text(
                    "${creator.rating} (${creator.reviews} reviews)",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    creator.distance,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Column(horizontalAlignment = Alignment.End) {
            Text(
                creator.price,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "starting price",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun Tag(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

// Data Classes
data class CreatorPreview(
    val name: String,
    val specialty: String,
    val rating: Double,
    val distance: String,
    val price: String,
    val image: String?
)

data class CreatorSearch(
    val name: String,
    val specialty: String,
    val rating: Double,
    val reviews: Int,
    val price: String,
    val distance: String,
    val verified: Boolean
)

private fun saveBitmapToCache(context: android.content.Context, bitmap: android.graphics.Bitmap): String? {
    return try {
        val cacheDir = context.cacheDir
        val file = java.io.File(cacheDir, "ref_${System.currentTimeMillis()}.jpg")
        java.io.FileOutputStream(file).use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
        }
        file.absolutePath
    } catch (e: java.lang.Exception) {
        e.printStackTrace()
        null
    }
}
