package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DocumentEntity
import com.example.engine.PdfGenerator
import com.example.ui.components.formatDate
import com.example.ui.components.formatFileSize
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewScreen(
    document: DocumentEntity,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onOpenExternal: () -> Unit
) {
    val context = LocalContext.current
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var showInfoSheet by remember { mutableStateOf(false) }

    // Zoom and pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(document.filePath) {
        isLoading = true
        val file = File(document.filePath)
        if (file.exists()) {
            pages = PdfGenerator.renderPdfPages(file)
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = document.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        if (pages.isNotEmpty()) {
                            Text(
                                text = "Page ${currentPageIndex + 1} of ${pages.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("pdf_view_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showInfoSheet = true }) {
                        Icon(Icons.Filled.Info, contentDescription = "Document details")
                    }
                    IconButton(onClick = onShare) {
                        Icon(Icons.Filled.Share, contentDescription = "Share PDF")
                    }
                    IconButton(onClick = onOpenExternal) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = "Open in external app")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (pages.isNotEmpty()) {
                Surface(
                    tonalElevation = 3.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        // Navigation controls & zoom
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (currentPageIndex > 0) {
                                        currentPageIndex--
                                        scale = 1f
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                },
                                enabled = currentPageIndex > 0
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous page")
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    scale = (scale - 0.25f).coerceAtLeast(0.75f)
                                }) {
                                    Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom out")
                                }
                                Text(
                                    text = "${(scale * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                )
                                IconButton(onClick = {
                                    scale = (scale + 0.25f).coerceAtMost(3.0f)
                                }) {
                                    Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom in")
                                }
                            }

                            IconButton(
                                onClick = {
                                    if (currentPageIndex < pages.size - 1) {
                                        currentPageIndex++
                                        scale = 1f
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                },
                                enabled = currentPageIndex < pages.size - 1
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next page")
                            }
                        }

                        // Page thumbnails filmstrip
                        if (pages.size > 1) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(pages) { idx, pageBmp ->
                                    val isCurrent = idx == currentPageIndex
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp, 60.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isCurrent) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            .padding(if (isCurrent) 2.dp else 0.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                currentPageIndex = idx
                                                scale = 1f
                                                offsetX = 0f
                                                offsetY = 0f
                                            }
                                    ) {
                                        Image(
                                            bitmap = pageBmp.asImageBitmap(),
                                            contentDescription = "Page ${idx + 1}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Rendering PDF pages...", style = MaterialTheme.typography.bodyMedium)
                }
            } else if (pages.isEmpty()) {
                Text(
                    text = "Unable to preview PDF on this device.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val currentBitmap = pages.getOrNull(currentPageIndex)
                if (currentBitmap != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(0.8f, 3.5f)
                                    if (scale > 1f) {
                                        offsetX += pan.x
                                        offsetY += pan.y
                                    } else {
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offsetX,
                                    translationY = offsetY
                                )
                                .padding(16.dp)
                        ) {
                            Image(
                                bitmap = currentBitmap.asImageBitmap(),
                                contentDescription = "Rendered page ${currentPageIndex + 1}",
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }
        }
    }

    // Document Details Sheet
    if (showInfoSheet) {
        ModalBottomSheet(
            onDismissRequest = { showInfoSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Document Details",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(18.dp))

                DetailRow("Title", document.title)
                DetailRow("Page Count", "${document.pageCount} pages")
                DetailRow("File Size", formatFileSize(document.fileSizeBytes))
                DetailRow("Page Format", document.pageSize)
                DetailRow("Orientation", document.orientation)
                DetailRow("Quality", document.compressionQuality)
                DetailRow("Category", document.category)
                DetailRow("Created", formatDate(document.createdAt))
                DetailRow("File Location", document.filePath)

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2
        )
    }
}
