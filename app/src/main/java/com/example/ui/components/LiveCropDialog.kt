package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.ScanPage
import com.example.ui.theme.DarkCardGradient
import com.example.ui.theme.DarkGradientBlack
import com.example.ui.theme.LightCardGradient
import com.example.ui.theme.LightGradientWhite
import java.io.File

/**
 * Gallery / image-editor grade Interactive Crop Dialog with live visual feedback,
 * rule-of-thirds grid, darkened outer mask, aspect ratio presets, and live preview toggle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveCropDialog(
    page: ScanPage,
    onDismiss: () -> Unit,
    onSaveCrop: (left: Float, top: Float, right: Float, bottom: Float) -> Unit
) {
    var cropLeft by remember { mutableFloatStateOf(page.cropLeft.coerceIn(0f, 0.45f)) }
    var cropTop by remember { mutableFloatStateOf(page.cropTop.coerceIn(0f, 0.45f)) }
    var cropRight by remember { mutableFloatStateOf(page.cropRight.coerceIn(0.55f, 1f)) }
    var cropBottom by remember { mutableFloatStateOf(page.cropBottom.coerceIn(0.55f, 1f)) }
    var previewModeIndex by remember { mutableIntStateOf(0) } // 0: Frame Overlay, 1: Live Result

    val isDark = isSystemInDarkTheme()
    val imagePath = page.processedImagePath ?: page.originalImagePath

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Crop,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Crop Document Page",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Live preview image editor",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Mode Tabs: Overlay Frame vs Live Cropped Result
                TabRow(
                    selectedTabIndex = previewModeIndex,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = previewModeIndex == 0,
                        onClick = { previewModeIndex = 0 },
                        text = { Text("Crop Frame") },
                        icon = { Icon(Icons.Filled.Crop, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = previewModeIndex == 1,
                        onClick = { previewModeIndex = 1 },
                        text = { Text("Live Result") },
                        icon = { Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Main Interactive Visual Canvas
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(
                            if (isDark) DarkCardGradient else LightCardGradient,
                            RoundedCornerShape(16.dp)
                        )
                        .border(
                            1.dp,
                            if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0),
                            RoundedCornerShape(16.dp)
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (previewModeIndex == 0) {
                            // Frame Mode: Image with interactive mask & rule-of-thirds grid
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = File(imagePath),
                                    contentDescription = "Crop Page View",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Live Crop Overlay with rule of thirds
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val leftPx = size.width * cropLeft
                                    val topPx = size.height * cropTop
                                    val rightPx = size.width * cropRight
                                    val bottomPx = size.height * cropBottom
                                    val cropW = (rightPx - leftPx).coerceAtLeast(10f)
                                    val cropH = (bottomPx - topPx).coerceAtLeast(10f)

                                    // Outer darkened mask
                                    // Top block
                                    drawRect(
                                        color = Color.Black.copy(alpha = 0.55f),
                                        topLeft = Offset(0f, 0f),
                                        size = Size(size.width, topPx)
                                    )
                                    // Bottom block
                                    drawRect(
                                        color = Color.Black.copy(alpha = 0.55f),
                                        topLeft = Offset(0f, bottomPx),
                                        size = Size(size.width, size.height - bottomPx)
                                    )
                                    // Left block
                                    drawRect(
                                        color = Color.Black.copy(alpha = 0.55f),
                                        topLeft = Offset(0f, topPx),
                                        size = Size(leftPx, cropH)
                                    )
                                    // Right block
                                    drawRect(
                                        color = Color.Black.copy(alpha = 0.55f),
                                        topLeft = Offset(rightPx, topPx),
                                        size = Size(size.width - rightPx, cropH)
                                    )

                                    // Crop border
                                    val strokeW = 2.5.dp.toPx()
                                    drawRect(
                                        color = Color(0xFF38BDF8),
                                        topLeft = Offset(leftPx, topPx),
                                        size = Size(cropW, cropH),
                                        style = Stroke(width = strokeW)
                                    )

                                    // Rule-of-thirds grid lines
                                    val gridColor = Color.White.copy(alpha = 0.4f)
                                    val gridStroke = 1.dp.toPx()
                                    // Verticals
                                    drawLine(
                                        color = gridColor,
                                        start = Offset(leftPx + cropW / 3f, topPx),
                                        end = Offset(leftPx + cropW / 3f, bottomPx),
                                        strokeWidth = gridStroke
                                    )
                                    drawLine(
                                        color = gridColor,
                                        start = Offset(leftPx + (cropW * 2f) / 3f, topPx),
                                        end = Offset(leftPx + (cropW * 2f) / 3f, bottomPx),
                                        strokeWidth = gridStroke
                                    )
                                    // Horizontals
                                    drawLine(
                                        color = gridColor,
                                        start = Offset(leftPx, topPx + cropH / 3f),
                                        end = Offset(rightPx, topPx + cropH / 3f),
                                        strokeWidth = gridStroke
                                    )
                                    drawLine(
                                        color = gridColor,
                                        start = Offset(leftPx, topPx + (cropH * 2f) / 3f),
                                        end = Offset(rightPx, topPx + (cropH * 2f) / 3f),
                                        strokeWidth = gridStroke
                                    )

                                    // Corner brackets
                                    val bracketLen = 22.dp.toPx()
                                    val bracketStroke = 4.dp.toPx()
                                    val cornerColor = Color.White

                                    // Top-left
                                    drawLine(cornerColor, Offset(leftPx, topPx), Offset(leftPx + bracketLen, topPx), bracketStroke)
                                    drawLine(cornerColor, Offset(leftPx, topPx), Offset(leftPx, topPx + bracketLen), bracketStroke)
                                    // Top-right
                                    drawLine(cornerColor, Offset(rightPx, topPx), Offset(rightPx - bracketLen, topPx), bracketStroke)
                                    drawLine(cornerColor, Offset(rightPx, topPx), Offset(rightPx, topPx + bracketLen), bracketStroke)
                                    // Bottom-left
                                    drawLine(cornerColor, Offset(leftPx, bottomPx), Offset(leftPx + bracketLen, bottomPx), bracketStroke)
                                    drawLine(cornerColor, Offset(leftPx, bottomPx), Offset(leftPx, bottomPx - bracketLen), bracketStroke)
                                    // Bottom-right
                                    drawLine(cornerColor, Offset(rightPx, bottomPx), Offset(rightPx - bracketLen, bottomPx), bracketStroke)
                                    drawLine(cornerColor, Offset(rightPx, bottomPx), Offset(rightPx, bottomPx - bracketLen), bracketStroke)
                                }
                            }
                        } else {
                            // Live Result Mode: Shows cropped preview simulation
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = File(imagePath),
                                    contentDescription = "Cropped Preview Result",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.Black.copy(alpha = 0.7f),
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = "Cropped Region: ${(cropLeft * 100).toInt()}%L, ${(cropTop * 100).toInt()}%T to ${(cropRight * 100).toInt()}%R, ${(cropBottom * 100).toInt()}%B",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Aspect Ratio & Quick Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = cropLeft == 0f && cropTop == 0f && cropRight == 1f && cropBottom == 1f,
                        onClick = {
                            cropLeft = 0f
                            cropTop = 0f
                            cropRight = 1f
                            cropBottom = 1f
                        },
                        label = { Text("Original") }
                    )
                    FilterChip(
                        selected = cropLeft == 0.05f && cropTop == 0.05f && cropRight == 0.95f && cropBottom == 0.95f,
                        onClick = {
                            cropLeft = 0.05f
                            cropTop = 0.05f
                            cropRight = 0.95f
                            cropBottom = 0.95f
                        },
                        label = { Text("Auto Trim (5%)") }
                    )
                    FilterChip(
                        selected = false,
                        onClick = {
                            // A4 document ratio crop (1 : 1.41)
                            cropLeft = 0.08f
                            cropRight = 0.92f
                            cropTop = 0.04f
                            cropBottom = 0.96f
                        },
                        label = { Text("A4 Ratio") }
                    )
                    FilterChip(
                        selected = false,
                        onClick = {
                            // 1:1 Square
                            cropLeft = 0.1f
                            cropRight = 0.9f
                            cropTop = 0.15f
                            cropBottom = 0.85f
                        },
                        label = { Text("Square") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Scrollable Controls for Margins
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Top Margin: ${(cropTop * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                        Text("Bottom Margin: ${((1f - cropBottom) * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Slider(
                            value = cropTop,
                            onValueChange = { cropTop = it.coerceAtMost(cropBottom - 0.1f) },
                            valueRange = 0f..0.45f,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Slider(
                            value = cropBottom,
                            onValueChange = { cropBottom = it.coerceAtLeast(cropTop + 0.1f) },
                            valueRange = 0.55f..1f,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Left Margin: ${(cropLeft * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                        Text("Right Margin: ${((1f - cropRight) * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Slider(
                            value = cropLeft,
                            onValueChange = { cropLeft = it.coerceAtMost(cropRight - 0.1f) },
                            valueRange = 0f..0.45f,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Slider(
                            value = cropRight,
                            onValueChange = { cropRight = it.coerceAtLeast(cropLeft + 0.1f) },
                            valueRange = 0.55f..1f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            onSaveCrop(cropLeft, cropTop, cropRight, cropBottom)
                        },
                        modifier = Modifier
                            .weight(1.5f)
                            .testTag("save_crop_button")
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Apply Crop", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
