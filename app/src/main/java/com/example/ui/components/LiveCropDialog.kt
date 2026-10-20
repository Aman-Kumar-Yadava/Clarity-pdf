package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.ScanPage
import com.example.engine.DocumentEnhancer
import com.example.ui.theme.DarkCardGradient
import com.example.ui.theme.DarkGradientBlack
import com.example.ui.theme.LightCardGradient
import com.example.ui.theme.LightGradientWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private enum class DragTarget {
    NONE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    EDGE_TOP,
    EDGE_BOTTOM,
    EDGE_LEFT,
    EDGE_RIGHT,
    BODY
}

/**
 * Gallery / Image-Editor grade Interactive Crop Dialog with:
 * - Exact 1:1 pixel alignment between displayed image and crop coordinates
 * - Touch-to-drag corner handles and edge handles directly on the canvas
 * - Drag-to-move interior crop box
 * - Aspect ratio presets (Full, A4 Document, ID Card, Square, Clean Margin)
 * - Live Result preview tab showing the cropped output
 * - Micro-adjustment precision sliders
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveCropDialog(
    page: ScanPage,
    onDismiss: () -> Unit,
    onSaveCrop: (left: Float, top: Float, right: Float, bottom: Float) -> Unit
) {
    var cropLeft by remember { mutableFloatStateOf(page.cropLeft.coerceIn(0f, 0.9f)) }
    var cropTop by remember { mutableFloatStateOf(page.cropTop.coerceIn(0f, 0.9f)) }
    var cropRight by remember { mutableFloatStateOf(page.cropRight.coerceIn(0.1f, 1f)) }
    var cropBottom by remember { mutableFloatStateOf(page.cropBottom.coerceIn(0.1f, 1f)) }

    // Ensure initial consistency
    if (cropRight <= cropLeft + 0.05f) cropRight = (cropLeft + 0.1f).coerceAtMost(1f)
    if (cropBottom <= cropTop + 0.05f) cropBottom = (cropTop + 0.1f).coerceAtMost(1f)

    var previewModeIndex by remember { mutableIntStateOf(0) } // 0: Crop Frame, 1: Live Result
    val isDark = isSystemInDarkTheme()

    // Load uncropped base image with user rotation + EXIF rotation applied
    var uncroppedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingBitmap by remember { mutableStateOf(true) }

    LaunchedEffect(page.id, page.originalImagePath, page.rotationDegrees) {
        isLoadingBitmap = true
        withContext(Dispatchers.IO) {
            try {
                val exif = DocumentEnhancer.getExifOrientationDegrees(page.originalImagePath)
                val totalRotation = (page.rotationDegrees + exif) % 360
                val decoded = DocumentEnhancer.decodeSampledBitmap(page.originalImagePath, 1200, 1200)
                if (decoded != null) {
                    val rotated = if (totalRotation != 0) {
                        val matrix = Matrix().apply { postRotate(totalRotation.toFloat()) }
                        val rotBmp = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                        if (rotBmp != decoded) decoded.recycle()
                        rotBmp
                    } else {
                        decoded
                    }
                    uncroppedBitmap = rotated
                }
            } catch (_: Exception) {}
            isLoadingBitmap = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
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
                                text = "Drag corners or edges to adjust crop",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Mode Tabs: Crop Frame vs Live Result
                TabRow(
                    selectedTabIndex = previewModeIndex,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = previewModeIndex == 0,
                        onClick = { previewModeIndex = 0 },
                        text = { Text("Crop Editor") },
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
                val canvasCardShape = RoundedCornerShape(16.dp)
                Card(
                    shape = canvasCardShape,
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(canvasCardShape)
                        .background(
                            if (isDark) DarkCardGradient else LightCardGradient,
                            canvasCardShape
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoadingBitmap || uncroppedBitmap == null) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Loading image...", style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            val bitmap = uncroppedBitmap!!
                            val density = LocalDensity.current

                            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                val boxW = constraints.maxWidth.toFloat()
                                val boxH = constraints.maxHeight.toFloat()

                                val bmpW = bitmap.width.toFloat()
                                val bmpH = bitmap.height.toFloat()

                                val scale = min(boxW / bmpW, boxH / bmpH)
                                val displayedW = bmpW * scale
                                val displayedH = bmpH * scale
                                val imgLeft = (boxW - displayedW) / 2f
                                val imgTop = (boxH - displayedH) / 2f

                                val minCropFraction = 0.05f

                                var activeTarget by remember { mutableStateOf(DragTarget.NONE) }

                                if (previewModeIndex == 0) {
                                    // Mode 0: Interactive Crop Editor with Gesture Handling
                                    val touchThresholdPx = with(density) { 36.dp.toPx() }

                                    Canvas(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .pointerInput(displayedW, displayedH, imgLeft, imgTop) {
                                                detectDragGestures(
                                                    onDragStart = { offset ->
                                                        val curL = imgLeft + cropLeft * displayedW
                                                        val curT = imgTop + cropTop * displayedH
                                                        val curR = imgLeft + cropRight * displayedW
                                                        val curB = imgTop + cropBottom * displayedH

                                                        val dTL = hypot(offset.x - curL, offset.y - curT)
                                                        val dTR = hypot(offset.x - curR, offset.y - curT)
                                                        val dBL = hypot(offset.x - curL, offset.y - curB)
                                                        val dBR = hypot(offset.x - curR, offset.y - curB)

                                                        val midX = (curL + curR) / 2f
                                                        val midY = (curT + curB) / 2f

                                                        val dEdgeT = hypot(offset.x - midX, offset.y - curT)
                                                        val dEdgeB = hypot(offset.x - midX, offset.y - curB)
                                                        val dEdgeL = hypot(offset.x - curL, offset.y - midY)
                                                        val dEdgeR = hypot(offset.x - curR, offset.y - midY)

                                                        activeTarget = when {
                                                            dTL <= touchThresholdPx -> DragTarget.TOP_LEFT
                                                            dTR <= touchThresholdPx -> DragTarget.TOP_RIGHT
                                                            dBL <= touchThresholdPx -> DragTarget.BOTTOM_LEFT
                                                            dBR <= touchThresholdPx -> DragTarget.BOTTOM_RIGHT
                                                            dEdgeT <= touchThresholdPx -> DragTarget.EDGE_TOP
                                                            dEdgeB <= touchThresholdPx -> DragTarget.EDGE_BOTTOM
                                                            dEdgeL <= touchThresholdPx -> DragTarget.EDGE_LEFT
                                                            dEdgeR <= touchThresholdPx -> DragTarget.EDGE_RIGHT
                                                            offset.x in curL..curR && offset.y in curT..curB -> DragTarget.BODY
                                                            else -> DragTarget.NONE
                                                        }
                                                    },
                                                    onDragEnd = { activeTarget = DragTarget.NONE },
                                                    onDragCancel = { activeTarget = DragTarget.NONE },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        if (displayedW <= 0f || displayedH <= 0f) return@detectDragGestures

                                                        val dx = dragAmount.x / displayedW
                                                        val dy = dragAmount.y / displayedH

                                                        when (activeTarget) {
                                                            DragTarget.TOP_LEFT -> {
                                                                cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - minCropFraction)
                                                                cropTop = (cropTop + dy).coerceIn(0f, cropBottom - minCropFraction)
                                                            }
                                                            DragTarget.TOP_RIGHT -> {
                                                                cropRight = (cropRight + dx).coerceIn(cropLeft + minCropFraction, 1f)
                                                                cropTop = (cropTop + dy).coerceIn(0f, cropBottom - minCropFraction)
                                                            }
                                                            DragTarget.BOTTOM_LEFT -> {
                                                                cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - minCropFraction)
                                                                cropBottom = (cropBottom + dy).coerceIn(cropTop + minCropFraction, 1f)
                                                            }
                                                            DragTarget.BOTTOM_RIGHT -> {
                                                                cropRight = (cropRight + dx).coerceIn(cropLeft + minCropFraction, 1f)
                                                                cropBottom = (cropBottom + dy).coerceIn(cropTop + minCropFraction, 1f)
                                                            }
                                                            DragTarget.EDGE_TOP -> {
                                                                cropTop = (cropTop + dy).coerceIn(0f, cropBottom - minCropFraction)
                                                            }
                                                            DragTarget.EDGE_BOTTOM -> {
                                                                cropBottom = (cropBottom + dy).coerceIn(cropTop + minCropFraction, 1f)
                                                            }
                                                            DragTarget.EDGE_LEFT -> {
                                                                cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - minCropFraction)
                                                            }
                                                            DragTarget.EDGE_RIGHT -> {
                                                                cropRight = (cropRight + dx).coerceIn(cropLeft + minCropFraction, 1f)
                                                            }
                                                            DragTarget.BODY -> {
                                                                val w = cropRight - cropLeft
                                                                val h = cropBottom - cropTop
                                                                val newL = (cropLeft + dx).coerceIn(0f, 1f - w)
                                                                val newT = (cropTop + dy).coerceIn(0f, 1f - h)
                                                                cropLeft = newL
                                                                cropRight = newL + w
                                                                cropTop = newT
                                                                cropBottom = newT + h
                                                            }
                                                            DragTarget.NONE -> {}
                                                        }
                                                    }
                                                )
                                            }
                                    ) {
                                        // 1. Draw Base Image
                                        drawImage(
                                            image = bitmap.asImageBitmap(),
                                            dstOffset = IntOffset(imgLeft.toInt(), imgTop.toInt()),
                                            dstSize = IntSize(displayedW.toInt(), displayedH.toInt())
                                        )

                                        // 2. Compute Crop Frame in Canvas coordinates
                                        val cLeft = imgLeft + cropLeft * displayedW
                                        val cTop = imgTop + cropTop * displayedH
                                        val cRight = imgLeft + cropRight * displayedW
                                        val cBottom = imgTop + cropBottom * displayedH
                                        val cWidth = max(4f, cRight - cLeft)
                                        val cHeight = max(4f, cBottom - cTop)

                                        // 3. Darkened Outer Mask
                                        val maskColor = Color.Black.copy(alpha = 0.58f)
                                        // Top
                                        drawRect(maskColor, Offset(0f, 0f), Size(size.width, cTop))
                                        // Bottom
                                        drawRect(maskColor, Offset(0f, cBottom), Size(size.width, size.height - cBottom))
                                        // Left
                                        drawRect(maskColor, Offset(0f, cTop), Size(cLeft, cHeight))
                                        // Right
                                        drawRect(maskColor, Offset(cRight, cTop), Size(size.width - cRight, cHeight))

                                        // 4. Crop Frame Border
                                        val strokePx = 2.5.dp.toPx()
                                        val frameColor = Color(0xFF38BDF8) // Vibrant sky blue
                                        drawRect(
                                            color = frameColor,
                                            topLeft = Offset(cLeft, cTop),
                                            size = Size(cWidth, cHeight),
                                            style = Stroke(width = strokePx)
                                        )

                                        // 5. Rule-of-thirds grid lines
                                        val gridColor = Color.White.copy(alpha = 0.35f)
                                        val gridStroke = 1.dp.toPx()
                                        // Vertical 1/3 and 2/3
                                        drawLine(gridColor, Offset(cLeft + cWidth / 3f, cTop), Offset(cLeft + cWidth / 3f, cBottom), gridStroke)
                                        drawLine(gridColor, Offset(cLeft + (cWidth * 2f) / 3f, cTop), Offset(cLeft + (cWidth * 2f) / 3f, cBottom), gridStroke)
                                        // Horizontal 1/3 and 2/3
                                        drawLine(gridColor, Offset(cLeft, cTop + cHeight / 3f), Offset(cRight, cTop + cHeight / 3f), gridStroke)
                                        drawLine(gridColor, Offset(cLeft, cTop + (cHeight * 2f) / 3f), Offset(cRight, cTop + (cHeight * 2f) / 3f), gridStroke)

                                        // 6. Corner Handles (L-shaped thick brackets + white circle knobs)
                                        val bracketLen = 22.dp.toPx()
                                        val bracketThick = 4.dp.toPx()
                                        val cornerColor = Color.White

                                        // Top-left
                                        drawLine(cornerColor, Offset(cLeft, cTop), Offset(cLeft + bracketLen, cTop), bracketThick)
                                        drawLine(cornerColor, Offset(cLeft, cTop), Offset(cLeft, cTop + bracketLen), bracketThick)
                                        drawCircle(Color.White, radius = 6.dp.toPx(), center = Offset(cLeft, cTop))
                                        drawCircle(frameColor, radius = 6.dp.toPx(), center = Offset(cLeft, cTop), style = Stroke(2.dp.toPx()))

                                        // Top-right
                                        drawLine(cornerColor, Offset(cRight, cTop), Offset(cRight - bracketLen, cTop), bracketThick)
                                        drawLine(cornerColor, Offset(cRight, cTop), Offset(cRight, cTop + bracketLen), bracketThick)
                                        drawCircle(Color.White, radius = 6.dp.toPx(), center = Offset(cRight, cTop))
                                        drawCircle(frameColor, radius = 6.dp.toPx(), center = Offset(cRight, cTop), style = Stroke(2.dp.toPx()))

                                        // Bottom-left
                                        drawLine(cornerColor, Offset(cLeft, cBottom), Offset(cLeft + bracketLen, cBottom), bracketThick)
                                        drawLine(cornerColor, Offset(cLeft, cBottom), Offset(cLeft, cBottom - bracketLen), bracketThick)
                                        drawCircle(Color.White, radius = 6.dp.toPx(), center = Offset(cLeft, cBottom))
                                        drawCircle(frameColor, radius = 6.dp.toPx(), center = Offset(cLeft, cBottom), style = Stroke(2.dp.toPx()))

                                        // Bottom-right
                                        drawLine(cornerColor, Offset(cRight, cBottom), Offset(cRight - bracketLen, cBottom), bracketThick)
                                        drawLine(cornerColor, Offset(cRight, cBottom), Offset(cRight, cBottom - bracketLen), bracketThick)
                                        drawCircle(Color.White, radius = 6.dp.toPx(), center = Offset(cRight, cBottom))
                                        drawCircle(frameColor, radius = 6.dp.toPx(), center = Offset(cRight, cBottom), style = Stroke(2.dp.toPx()))

                                        // 7. Midpoint Edge Knobs
                                        val pillLen = 18.dp.toPx()
                                        val pillThick = 3.5.dp.toPx()
                                        val midX = (cLeft + cRight) / 2f
                                        val midY = (cTop + cBottom) / 2f

                                        // Top Edge Knob
                                        drawLine(cornerColor, Offset(midX - pillLen / 2f, cTop), Offset(midX + pillLen / 2f, cTop), pillThick)
                                        // Bottom Edge Knob
                                        drawLine(cornerColor, Offset(midX - pillLen / 2f, cBottom), Offset(midX + pillLen / 2f, cBottom), pillThick)
                                        // Left Edge Knob
                                        drawLine(cornerColor, Offset(cLeft, midY - pillLen / 2f), Offset(cLeft, midY + pillLen / 2f), pillThick)
                                        // Right Edge Knob
                                        drawLine(cornerColor, Offset(cRight, midY - pillLen / 2f), Offset(cRight, midY + pillLen / 2f), pillThick)
                                    }
                                } else {
                                    // Mode 1: Live Result (Accurate slice preview scaled to fill the box)
                                    val srcL = (cropLeft * bmpW).toInt().coerceIn(0, bitmap.width - 2)
                                    val srcT = (cropTop * bmpH).toInt().coerceIn(0, bitmap.height - 2)
                                    val srcR = (cropRight * bmpW).toInt().coerceIn(srcL + 2, bitmap.width)
                                    val srcB = (cropBottom * bmpH).toInt().coerceIn(srcT + 2, bitmap.height)

                                    val cropAspect = (srcR - srcL).toFloat() / (srcB - srcT).toFloat()
                                    val resScale = min(boxW / (srcR - srcL).toFloat(), boxH / (srcB - srcT).toFloat())
                                    val resW = (srcR - srcL) * resScale
                                    val resH = (srcB - srcT) * resScale
                                    val resL = (boxW - resW) / 2f
                                    val resT = (boxH - resH) / 2f

                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        drawImage(
                                            image = bitmap.asImageBitmap(),
                                            srcOffset = IntOffset(srcL, srcT),
                                            srcSize = IntSize(srcR - srcL, srcB - srcT),
                                            dstOffset = IntOffset(resL.toInt(), resT.toInt()),
                                            dstSize = IntSize(resW.toInt(), resH.toInt())
                                        )

                                        // Clean subtle border around the result
                                        drawRect(
                                            color = Color(0xFF38BDF8),
                                            topLeft = Offset(resL, resT),
                                            size = Size(resW, resH),
                                            style = Stroke(width = 1.5.dp.toPx())
                                        )
                                    }

                                    // Floating badge with exact aspect ratio
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.Black.copy(alpha = 0.72f),
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = "Cropped View (${(cropAspect * 100).toInt() / 100f} : 1)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Aspect Ratio & Quick Presets Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = cropLeft == 0f && cropTop == 0f && cropRight == 1f && cropBottom == 1f,
                        onClick = {
                            cropLeft = 0f
                            cropTop = 0f
                            cropRight = 1f
                            cropBottom = 1f
                        },
                        label = { Text("Full", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )

                    FilterChip(
                        selected = cropLeft == 0.04f && cropTop == 0.04f && cropRight == 0.96f && cropBottom == 0.96f,
                        onClick = {
                            cropLeft = 0.04f
                            cropTop = 0.04f
                            cropRight = 0.96f
                            cropBottom = 0.96f
                        },
                        label = { Text("Margin 4%", fontSize = 11.sp) }
                    )

                    FilterChip(
                        selected = false,
                        onClick = {
                            // A4 Aspect Ratio: 1 : 1.414
                            val currentAspect = (cropRight - cropLeft) / (cropBottom - cropTop)
                            val targetAspect = 1f / 1.414f // 0.707
                            if (currentAspect > targetAspect) {
                                val targetWidth = (cropBottom - cropTop) * targetAspect
                                val midX = (cropLeft + cropRight) / 2f
                                cropLeft = (midX - targetWidth / 2f).coerceAtLeast(0f)
                                cropRight = (cropLeft + targetWidth).coerceAtMost(1f)
                            } else {
                                val targetHeight = (cropRight - cropLeft) / targetAspect
                                val midY = (cropTop + cropBottom) / 2f
                                cropTop = (midY - targetHeight / 2f).coerceAtLeast(0f)
                                cropBottom = (cropTop + targetHeight).coerceAtMost(1f)
                            }
                        },
                        label = { Text("A4 Doc", fontSize = 11.sp) }
                    )

                    FilterChip(
                        selected = false,
                        onClick = {
                            // ID Card Aspect: 1.58 : 1
                            val targetAspect = 1.58f
                            val currentAspect = (cropRight - cropLeft) / (cropBottom - cropTop)
                            if (currentAspect > targetAspect) {
                                val targetWidth = (cropBottom - cropTop) * targetAspect
                                val midX = (cropLeft + cropRight) / 2f
                                cropLeft = (midX - targetWidth / 2f).coerceAtLeast(0f)
                                cropRight = (cropLeft + targetWidth).coerceAtMost(1f)
                            } else {
                                val targetHeight = (cropRight - cropLeft) / targetAspect
                                val midY = (cropTop + cropBottom) / 2f
                                cropTop = (midY - targetHeight / 2f).coerceAtLeast(0f)
                                cropBottom = (cropTop + targetHeight).coerceAtMost(1f)
                            }
                        },
                        label = { Text("ID Card", fontSize = 11.sp) }
                    )

                    FilterChip(
                        selected = false,
                        onClick = {
                            // Square: 1 : 1
                            val size = min(cropRight - cropLeft, cropBottom - cropTop)
                            val midX = (cropLeft + cropRight) / 2f
                            val midY = (cropTop + cropBottom) / 2f
                            cropLeft = (midX - size / 2f).coerceAtLeast(0f)
                            cropRight = (cropLeft + size).coerceAtMost(1f)
                            cropTop = (midY - size / 2f).coerceAtLeast(0f)
                            cropBottom = (cropTop + size).coerceAtMost(1f)
                        },
                        label = { Text("1:1", fontSize = 11.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Fine-tune Precision Sliders
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(115.dp)
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
                            onValueChange = { cropTop = it.coerceAtMost(cropBottom - 0.05f) },
                            valueRange = 0f..0.85f,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Slider(
                            value = cropBottom,
                            onValueChange = { cropBottom = it.coerceAtLeast(cropTop + 0.05f) },
                            valueRange = 0.15f..1f,
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
                            onValueChange = { cropLeft = it.coerceAtMost(cropRight - 0.05f) },
                            valueRange = 0f..0.85f,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Slider(
                            value = cropRight,
                            onValueChange = { cropRight = it.coerceAtLeast(cropLeft + 0.05f) },
                            valueRange = 0.15f..1f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

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
