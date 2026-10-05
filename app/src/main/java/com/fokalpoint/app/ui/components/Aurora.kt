package com.fokalpoint.app.ui.components

import android.annotation.SuppressLint
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.fokalpoint.app.ui.theme.Aurora
import java.text.NumberFormat
import java.util.Locale

// ------------------------------------------------------------------ canvas

/** Deep-space background with soft violet / cyan / pink light pools. Static (no endless animation). */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(Aurora.Void)
            .drawBehind {
                val w = size.width
                val h = size.height
                drawCircle(
                    Brush.radialGradient(listOf(Aurora.Violet.copy(alpha = 0.32f), Color.Transparent), Offset(w * 0.1f, h * 0.05f), w * 0.85f),
                    radius = w * 0.85f, center = Offset(w * 0.1f, h * 0.05f)
                )
                drawCircle(
                    Brush.radialGradient(listOf(Aurora.Cyan.copy(alpha = 0.18f), Color.Transparent), Offset(w * 0.95f, h * 0.32f), w * 0.7f),
                    radius = w * 0.7f, center = Offset(w * 0.95f, h * 0.32f)
                )
                drawCircle(
                    Brush.radialGradient(listOf(Aurora.Pink.copy(alpha = 0.12f), Color.Transparent), Offset(w * 0.2f, h * 0.95f), w * 0.8f),
                    radius = w * 0.8f, center = Offset(w * 0.2f, h * 0.95f)
                )
            },
        content = content
    )
}

// ------------------------------------------------------------------ surfaces

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    highlight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, label = "press")
    Column(
        modifier
            .scale(scale)
            .clip(shape)
            .background(if (highlight) Aurora.GlassStrong else Aurora.Glass)
            .border(1.dp, if (highlight) Aurora.Signature else Aurora.GlassBorder, shape)
            .then(if (onClick != null) Modifier.clickable(interaction, indication = null, onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content
    )
}

// ------------------------------------------------------------------ buttons

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    loading: Boolean = false,
    enabled: Boolean = true,
    brush: Brush = Aurora.Signature,
    height: Dp = 56.dp
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")
    val active = enabled && !loading
    Box(
        modifier
            .scale(scale)
            .height(height)
            .clip(CircleShape)
            .background(if (active) brush else SolidColor(Aurora.GlassStrong))
            .clickable(interaction, indication = null, enabled = active, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.5.dp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, null, tint = if (active) Color.White else Aurora.TextTertiary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(text, style = MaterialTheme.typography.labelLarge, color = if (active) Color.White else Aurora.TextTertiary)
            }
        }
    }
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = Aurora.TextPrimary,
    enabled: Boolean = true,
    height: Dp = 52.dp
) {
    Row(
        modifier
            .height(height)
            .clip(CircleShape)
            .background(Aurora.Glass)
            .border(1.dp, Aurora.Hairline, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) tint else Aurora.TextTertiary, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) tint else Aurora.TextTertiary)
    }
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Aurora.TextPrimary,
    size: Dp = 44.dp,
    background: Color = Color(0x99101018)
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .border(1.dp, Aurora.Hairline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.48f)) }
}

// ------------------------------------------------------------------ inputs

@Composable
fun AuroraTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    singleLine: Boolean = true,
    minLines: Int = 1,
    isError: Boolean = false,
    testTag: String = ""
) {
    val shape = RoundedCornerShape(18.dp)
    Column(modifier) {
        if (label.isNotEmpty()) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Aurora.TextSecondary)
            Spacer(Modifier.height(8.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Aurora.TextPrimary),
            cursorBrush = Aurora.Signature,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onAny = { onImeAction() }),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(Aurora.Glass)
                        .border(1.dp, if (isError) SolidColor(Aurora.Coral) else Aurora.GlassBorder, shape)
                        .padding(horizontal = 16.dp, vertical = if (singleLine) 16.dp else 14.dp),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top
                ) {
                    if (leadingIcon != null) {
                        Icon(leadingIcon, null, tint = Aurora.TextTertiary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                    }
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = Aurora.TextTertiary)
                        }
                        inner()
                    }
                    trailing?.invoke()
                }
            }
        )
    }
}

// ------------------------------------------------------------------ chips & badges

@Composable
fun AuroraChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val bg by animateColorAsState(if (selected) Aurora.Violet.copy(alpha = 0.22f) else Aurora.Glass, label = "chip")
    Row(
        modifier
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, if (selected) Aurora.Signature else SolidColor(Aurora.Hairline), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) Aurora.TextPrimary else Aurora.TextSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (selected) Aurora.TextPrimary else Aurora.TextSecondary)
    }
}

@Composable
fun ProBadge(modifier: Modifier = Modifier, compact: Boolean = false) {
    Box(
        modifier
            .clip(CircleShape)
            .background(Aurora.ProBrush)
            .padding(horizontal = if (compact) 7.dp else 10.dp, vertical = if (compact) 2.dp else 4.dp)
    ) {
        Text("PRO", style = MaterialTheme.typography.labelSmall, color = Aurora.Void, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.35f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) { Text(text, style = MaterialTheme.typography.labelSmall, color = color) }
}

fun statusColor(status: String): Color = when (status) {
    "Pending" -> Aurora.Amber
    "Accepted" -> Aurora.Cyan
    "Confirmed" -> Aurora.VioletSoft
    "Completed" -> Aurora.Mint
    "Cancelled" -> Aurora.Coral
    else -> Aurora.TextSecondary
}

@Composable
fun RatingLabel(rating: Double, count: Int, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Star, null, tint = Aurora.Amber, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(3.dp))
        if (count == 0 || rating <= 0.0) {
            Text("New", style = MaterialTheme.typography.labelMedium, color = Aurora.TextPrimary)
        } else {
            Text(String.format(Locale.US, "%.1f", rating), style = MaterialTheme.typography.labelMedium, color = Aurora.TextPrimary)
            Text(" ($count)", style = MaterialTheme.typography.labelMedium, color = Aurora.TextTertiary)
        }
    }
}

@Composable
fun VerifiedMark(modifier: Modifier = Modifier) {
    Icon(Icons.Rounded.Verified, "Verified", tint = Aurora.Cyan, modifier = modifier.size(16.dp))
}

// ------------------------------------------------------------------ images

/**
 * Image model for Coil. Sample data bundled in the APK uses `res:<drawable>`; everything
 * else (Supabase Storage URLs, content:// URIs) passes through unchanged.
 */
@SuppressLint("DiscouragedApi")
@Composable
fun imageModel(source: String): Any? {
    if (source.isBlank()) return null
    if (!source.startsWith("res:")) return source
    val context = LocalContext.current
    val id = remember(source) { context.resources.getIdentifier(source.removePrefix("res:"), "drawable", context.packageName) }
    return id.takeIf { it != 0 }
}

@Composable
fun FokalImage(
    source: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    Box(modifier.background(Brush.linearGradient(listOf(Color(0xFF1B1630), Color(0xFF0F1E26))))) {
        AsyncImage(
            model = imageModel(source),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier.matchParentSize()
        )
    }
}

@Composable
fun Avatar(source: String, name: String, size: Dp, modifier: Modifier = Modifier, proRing: Boolean = false) {
    val ring = if (proRing) Aurora.ProBrush else Aurora.GlassBorder
    Box(
        modifier
            .size(size)
            .border(if (proRing) 2.dp else 1.dp, ring, CircleShape)
            .padding(if (proRing) 3.dp else 1.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Aurora.Violet.copy(alpha = 0.6f), Aurora.Cyan.copy(alpha = 0.6f)))),
        contentAlignment = Alignment.Center
    ) {
        val initials = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
        Text(initials.ifEmpty { "F" }, style = MaterialTheme.typography.titleMedium, color = Color.White)
        if (source.isNotBlank()) {
            AsyncImage(model = imageModel(source), contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
    }
}

// ------------------------------------------------------------------ layout helpers

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Aurora.TextPrimary, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(action, style = MaterialTheme.typography.labelMedium, color = Aurora.VioletSoft,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onAction).padding(horizontal = 8.dp, vertical = 4.dp))
        }
    }
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(14.dp))
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Aurora.TextPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(76.dp).clip(CircleShape).background(Aurora.Glass).border(1.dp, Aurora.Signature, CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = Aurora.VioletSoft, modifier = Modifier.size(32.dp)) }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = Aurora.TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Aurora.TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            GradientButton(action, onAction, height = 48.dp)
        }
    }
}

@Composable
fun GradientText(text: String, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier, brush: Brush = Aurora.Signature) {
    Text(text, style = style.copy(brush = brush), modifier = modifier)
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, accent: Color = Aurora.VioletSoft) {
    GlassCard(modifier, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = accent, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = Aurora.TextSecondary, maxLines = 1)
    }
}

@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Aurora.Hairline))
}

private val inr = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 }
fun formatInr(amount: Double): String = inr.format(amount)

/** ₹1.2L / ₹45K style for tight spaces. */
fun formatInrShort(amount: Double): String = when {
    amount >= 100_000 -> "₹" + String.format(Locale.US, "%.1fL", amount / 100_000).replace(".0L", "L")
    amount >= 1_000 -> "₹" + String.format(Locale.US, "%.0fK", amount / 1_000)
    else -> formatInr(amount)
}

@Composable
fun BorderStrokeHairline() = BorderStroke(1.dp, Aurora.Hairline)
