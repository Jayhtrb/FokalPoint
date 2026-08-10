@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.example.ui.screens

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.PremiumHeader
import com.example.ui.viewmodel.FokalViewModel

@Composable
fun CreatorSignUpScreen(
    viewModel: FokalViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onSuccess: () -> Unit = {}
) {
    var specialization by remember { mutableStateOf("photographer") }
    var skillsets by remember { mutableStateOf(setOf<String>()) }
    var experienceLevel by remember { mutableStateOf("beginner") }
    var yearsOfExperience by remember { mutableStateOf("") }
    var instagramUrl by remember { mutableStateOf("") }
    var youtubeUrl by remember { mutableStateOf("") }
    var websiteUrl by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var equipment by remember { mutableStateOf("") }
    var languages by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Creator Profile",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
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
            // Progress Indicator
            LinearProgressIndicator(
                progress = 0.6f,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary
            )
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Header
                item {
                    PremiumHeader(
                        title = "Creator Style Details",
                        subtitle = "Showcase your specialties to local clients",
                        icon = Icons.Outlined.Star
                    )
                }
                
                // Primary Specialization
                item {
                    SpecializationSection(
                        selected = specialization,
                        onSelect = { specialization = it }
                    )
                }
                
                // Skillsets
                item {
                    SkillsetSection(
                        selectedSkills = skillsets,
                        onSkillsChange = { skillsets = it }
                    )
                }
                
                // Experience Profile
                item {
                    ExperienceSection(
                        selectedLevel = experienceLevel,
                        onLevelChange = { experienceLevel = it },
                        yearsOfExperience = yearsOfExperience,
                        onYearsChange = { yearsOfExperience = it }
                    )
                }
                
                // Social Portfolios (with URL validation)
                item {
                    SocialPortfolioSection(
                        instagramUrl = instagramUrl,
                        onInstagramChange = { instagramUrl = it },
                        youtubeUrl = youtubeUrl,
                        onYoutubeChange = { youtubeUrl = it },
                        websiteUrl = websiteUrl,
                        onWebsiteChange = { websiteUrl = it },
                        showError = showError,
                        errorMessage = errorMessage
                    )
                }
                
                // Additional Info
                item {
                    AdditionalInfoSection(
                        bio = bio,
                        onBioChange = { bio = it },
                        equipment = equipment,
                        onEquipmentChange = { equipment = it },
                        languages = languages,
                        onLanguagesChange = { languages = it }
                    )
                }
                
                // Submit Button
                item {
                    Button(
                        onClick = {
                            // Validate URLs before submission
                            val validationError = validateUrls(
                                instagramUrl = instagramUrl,
                                youtubeUrl = youtubeUrl,
                                websiteUrl = websiteUrl
                            )
                            
                            if (validationError != null) {
                                showError = true
                                errorMessage = validationError
                                return@Button
                            }
                            
                            isSubmitting = true
                            viewModel.createCreatorProfile(
                                specialization = specialization,
                                skillsets = skillsets,
                                experienceLevel = experienceLevel,
                                yearsOfExperience = yearsOfExperience.toIntOrNull() ?: 0,
                                instagramUrl = formatUrl(instagramUrl),
                                youtubeUrl = formatUrl(youtubeUrl),
                                websiteUrl = formatUrl(websiteUrl),
                                bio = bio,
                                equipment = equipment.split(",").map { it.trim() },
                                languages = languages.split(",").map { it.trim() }
                            ) { success ->
                                isSubmitting = false
                                if (success) {
                                    onSuccess()
                                } else {
                                    showError = true
                                    errorMessage = "Failed to create profile. Please try again."
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = !isSubmitting
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White
                            )
                        } else {
                            Text("Complete Profile")
                        }
                    }
                }
                
                // Error Message
                if (showError) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = errorMessage,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// URL Validation and Formatting Functions
fun validateUrls(
    instagramUrl: String,
    youtubeUrl: String,
    websiteUrl: String
): String? {
    val urlRegex = Regex("^(https?://).*")
    
    // Check Instagram URL
    if (instagramUrl.isNotBlank() && !urlRegex.matches(instagramUrl)) {
        return "Instagram URL must start with http:// or https://"
    }
    
    // Check YouTube URL
    if (youtubeUrl.isNotBlank() && !urlRegex.matches(youtubeUrl)) {
        return "YouTube URL must start with http:// or https://"
    }
    
    // Check Website URL
    if (websiteUrl.isNotBlank() && !urlRegex.matches(websiteUrl)) {
        return "Website URL must start with http:// or https://"
    }
    
    return null
}

fun formatUrl(url: String): String {
    if (url.isBlank()) return ""
    
    // If URL doesn't have scheme, add https://
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
        // Handle common social media URLs
        return when {
            url.contains("instagram.com") || url.contains("instagram.") -> {
                if (url.startsWith("@")) {
                    "https://www.instagram.com/${url.substring(1)}"
                } else if (!url.contains("instagram.com")) {
                    "https://www.instagram.com/$url"
                } else {
                    "https://$url"
                }
            }
            url.contains("youtube.com") || url.contains("youtu.be") -> {
                "https://$url"
            }
            else -> {
                // Try to add https:// for website
                "https://$url"
            }
        }
    }
    
    return url
}

@Composable
fun SpecializationSection(
    selected: String,
    onSelect: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Primary Specialization",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "What's your primary creative focus?",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SpecializationChip(
                    label = "📸 Photographer",
                    selected = selected == "photographer",
                    onClick = { onSelect("photographer") }
                )
                SpecializationChip(
                    label = "🎥 Videographer",
                    selected = selected == "videographer",
                    onClick = { onSelect("videographer") }
                )
                SpecializationChip(
                    label = "🎬 Both",
                    selected = selected == "both",
                    onClick = { onSelect("both") }
                )
            }
        }
    }
}

@Composable
fun RowScope.SpecializationChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { 
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        modifier = Modifier.weight(1f),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = Color.White
        )
    )
}

@Composable
fun SkillsetSection(
    selectedSkills: Set<String>,
    onSkillsChange: (Set<String>) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Choose Your Skillsets",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Select multiple skills that represent your expertise",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            val skills = listOf(
                "📸 Photographer",
                "🎥 Videographer",
                "🎬 Real Creator",
                "✈️ Drone",
                "🎭 Candid",
                "🎞️ Traditional",
                "🎬 Cinematic",
                "🏢 Studio",
                "🌳 Outdoor",
                "🎨 Editor",
                "💡 Lighting",
                "🎵 Music"
            )
            
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                skills.forEach { skill ->
                    SkillChip(
                        label = skill,
                        selected = selectedSkills.contains(skill),
                        onToggle = {
                            val newSet = if (selectedSkills.contains(skill)) {
                                selectedSkills - skill
                            } else {
                                selectedSkills + skill
                            }
                            onSkillsChange(newSet)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SkillChip(
    label: String,
    selected: Boolean,
    onToggle: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onToggle,
        label = { 
            Text(
                label,
                style = MaterialTheme.typography.bodySmall
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
fun ExperienceSection(
    selectedLevel: String,
    onLevelChange: (String) -> Unit,
    yearsOfExperience: String,
    onYearsChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Experience Profile",
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Experience Level Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExperienceLevelCard(
                    level = "Beginner",
                    description = "Starting your creative journey",
                    icon = "🌱",
                    selected = selectedLevel == "beginner",
                    onClick = { onLevelChange("beginner") }
                )
                ExperienceLevelCard(
                    level = "Professional",
                    description = "Experienced with a portfolio",
                    icon = "⭐",
                    selected = selectedLevel == "professional",
                    onClick = { onLevelChange("professional") }
                )
                ExperienceLevelCard(
                    level = "Studio",
                    description = "Full-service studio owner",
                    icon = "🏢",
                    selected = selectedLevel == "studio",
                    onClick = { onLevelChange("studio") }
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Years of Experience
            OutlinedTextField(
                value = yearsOfExperience,
                onValueChange = { 
                    if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                        onYearsChange(it)
                    }
                },
                label = { Text("Years of Experience") },
                placeholder = { Text("e.g., 4") },
                leadingIcon = { Icon(Icons.Outlined.CalendarToday, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        }
    }
}

@Composable
fun RowScope.ExperienceLevelCard(
    level: String,
    description: String,
    icon: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (selected)
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = level,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun SocialPortfolioSection(
    instagramUrl: String,
    onInstagramChange: (String) -> Unit,
    youtubeUrl: String,
    onYoutubeChange: (String) -> Unit,
    websiteUrl: String,
    onWebsiteChange: (String) -> Unit,
    showError: Boolean,
    errorMessage: String
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Link Your Social Portfolios",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Add your social media links to showcase your work",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Instagram
            OutlinedTextField(
                value = instagramUrl,
                onValueChange = onInstagramChange,
                label = { Text("Instagram Username/URL") },
                placeholder = { Text("e.g., @yourname or instagram.com/yourname") },
                leadingIcon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = showError && errorMessage.contains("Instagram"),
                trailingIcon = {
                    if (instagramUrl.isNotBlank()) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = Color.Green
                        )
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // YouTube
            OutlinedTextField(
                value = youtubeUrl,
                onValueChange = onYoutubeChange,
                label = { Text("YouTube Channel Link") },
                placeholder = { Text("e.g., youtube.com/yourchannel") },
                leadingIcon = { Icon(Icons.Outlined.VideoLibrary, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = showError && errorMessage.contains("YouTube")
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Website
            OutlinedTextField(
                value = websiteUrl,
                onValueChange = onWebsiteChange,
                label = { Text("Website / Portfolio Address") },
                placeholder = { Text("e.g., yourportfolio.com") },
                leadingIcon = { Icon(Icons.Outlined.Language, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = showError && errorMessage.contains("Website"),
                trailingIcon = {
                    if (websiteUrl.isNotBlank()) {
                        IconButton(
                            onClick = {
                                // Preview website
                            }
                        ) {
                            Icon(
                                Icons.Outlined.OpenInNew,
                                contentDescription = "Preview",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            )
            
            // URL Helper
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "💡 URL Tips",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = """
                            • You can enter full URL (https://...) or just the handle
                            • Instagram: @username or instagram.com/username
                            • YouTube: channel URL or channel ID
                            • Website: yourdomain.com
                        """.trimIndent(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Error Message for URL
            if (showError && errorMessage.contains("URL")) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun AdditionalInfoSection(
    bio: String,
    onBioChange: (String) -> Unit,
    equipment: String,
    onEquipmentChange: (String) -> Unit,
    languages: String,
    onLanguagesChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Additional Information",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Tell clients more about yourself",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Bio
            OutlinedTextField(
                value = bio,
                onValueChange = onBioChange,
                label = { Text("Bio (Tell your story)") },
                placeholder = { Text("I'm a passionate photographer with 4 years of experience...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                maxLines = 4,
                leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Equipment
            OutlinedTextField(
                value = equipment,
                onValueChange = onEquipmentChange,
                label = { Text("Equipment (comma separated)") },
                placeholder = { Text("e.g., Sony A7III, 24-70mm, DJI Drone") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Camera, contentDescription = null) }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Languages
            OutlinedTextField(
                value = languages,
                onValueChange = onLanguagesChange,
                label = { Text("Languages (comma separated)") },
                placeholder = { Text("e.g., English, Hindi, Marathi") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Language, contentDescription = null) }
            )
        }
    }
}
