package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.viewmodel.FokalViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewSystem(
    bookingId: Long,
    creatorId: String,
    onReviewSubmitted: () -> Unit,
    viewModel: FokalViewModel = hiltViewModel()
) {
    var rating by remember { mutableStateOf(0f) }
    var reviewText by remember { mutableStateOf("") }
    var selectedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var selectedVideo by remember { mutableStateOf<Uri?>(null) }
    
    // Review Categories
    val categories = listOf(
        "Quality" to 0f,
        "Professionalism" to 0f,
        "Communication" to 0f,
        "Punctuality" to 0f,
        "Value" to 0f
    )
    
    val categoryRatings = remember { mutableStateMapOf<String, Float>() }
    
    // Pickers
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedImages = (selectedImages + uris).distinct()
        }
    }
    
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedVideo = uri
        }
    }
    
    val scrollState = rememberScrollState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Write a Review") },
                navigationIcon = {
                    IconButton(onClick = onReviewSubmitted) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Overall Rating
            Text(
                text = "Overall Rating",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            RatingStars(
                rating = rating,
                onRatingChange = { rating = it }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Category Ratings
            Text(
                text = "Rate by Category",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            
            categories.forEach { (label, _) ->
                CategoryRatingRow(
                    label = label,
                    rating = categoryRatings[label] ?: 0f,
                    onRatingChange = { categoryRatings[label] = it }
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Review Text
            OutlinedTextField(
                value = reviewText,
                onValueChange = { reviewText = it },
                label = { Text("Your Review") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                maxLines = 5,
                shape = RoundedCornerShape(12.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Media Upload
            Text(
                text = "Add Photos or Videos",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Photo picker
                Button(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.PhotoCamera, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Photos", fontSize = 13.sp)
                }
                
                // Video picker
                Button(
                    onClick = { videoPickerLauncher.launch("video/*") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.Videocam, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Video", fontSize = 13.sp)
                }
            }
            
            // Media preview
            if (selectedImages.isNotEmpty() || selectedVideo != null) {
                Spacer(modifier = Modifier.height(4.dp))
                MediaPreviewRow(
                    images = selectedImages,
                    video = selectedVideo,
                    onRemoveImage = { selectedImages = selectedImages - it },
                    onRemoveVideo = { selectedVideo = null }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Submit Button
            Button(
                onClick = {
                    viewModel.submitReview(
                        bookingId = bookingId,
                        creatorId = creatorId,
                        rating = rating,
                        review = reviewText,
                        categoryRatings = categoryRatings.toMap(),
                        images = selectedImages,
                        video = selectedVideo
                    )
                    onReviewSubmitted()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = rating > 0 && reviewText.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Submit Review", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun RatingStars(
    rating: Float,
    onRatingChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        (1..5).forEach { star ->
            IconButton(
                onClick = { onRatingChange(star.toFloat()) },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    if (star <= rating) 
                        Icons.Filled.Star 
                    else 
                        Icons.Outlined.Star,
                    contentDescription = null,
                    tint = if (star <= rating) 
                        Color(0xFFFFB800) 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CategoryRatingRow(
    label: String,
    rating: Float,
    onRatingChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(120.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            (1..5).forEach { star ->
                IconButton(
                    onClick = { onRatingChange(star.toFloat()) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        if (star <= rating) 
                            Icons.Filled.Star 
                        else 
                            Icons.Outlined.Star,
                        contentDescription = null,
                        tint = if (star <= rating) 
                            Color(0xFFFFB800) 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            rating.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun MediaPreviewRow(
    images: List<Uri>,
    video: Uri?,
    onRemoveImage: (Uri) -> Unit,
    onRemoveVideo: () -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(images) { uri ->
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                AsyncImage(
                    model = uri,
                    contentDescription = "Selected Photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                IconButton(
                    onClick = { onRemoveImage(uri) },
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.6f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove photo",
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
        
        video?.let { uri ->
            item {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Black.copy(alpha = 0.2f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Video file indicator",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = { onRemoveVideo() },
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.6f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove video",
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}
