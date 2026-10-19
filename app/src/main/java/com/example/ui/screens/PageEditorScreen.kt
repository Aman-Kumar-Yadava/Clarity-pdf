package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.CompressionQuality
import com.example.data.model.DocumentEntity
import com.example.data.model.EnhancementMode
import com.example.data.model.PdfOrientation
import com.example.data.model.PdfPageSize
import com.example.viewmodel.ScanViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageEditorScreen(
    viewModel: ScanViewModel,
    onBack: () -> Unit,
    onAddFromCamera: () -> Unit,
    onOpenGeneratedDoc: (DocumentEntity) -> Unit,
    onShareGeneratedDoc: (DocumentEntity) -> Unit,
    onDoneNavHome: () -> Unit
) {
    val context = LocalContext.current
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val selectedIndex by viewModel.selectedPageIndex.collectAsStateWithLifecycle()
    val generationState by viewModel.generationState.collectAsStateWithLifecycle()

    var showExportSheet by remember { mutableStateOf(false) }
    var showTuneSheet by remember { mutableStateOf(false) }
    var showCropDialog by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var applyFilterToAll by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importImages(context, uris)
        }
    }

    BackHandler {
        if (pages.isNotEmpty()) {
            showExitConfirm = true
        } else {
            onBack()
        }
    }

    val currentPage = pages.getOrNull(selectedIndex)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Document Studio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (pages.isNotEmpty()) "Page ${selectedIndex + 1} of ${pages.size}" else "0 pages",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (pages.isNotEmpty()) showExitConfirm = true else onBack()
                        },
                        modifier = Modifier.testTag("page_editor_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onAddFromCamera) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = "Camera")
                    }
                    IconButton(onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }) {
                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "Gallery")
                    }
                    IconButton(onClick = { viewModel.resetEdits(context) }) {
                        Icon(Icons.Filled.RestartAlt, contentDescription = "Reset edits")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            // Filmstrip carousel of pages
            Surface(
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(vertical = 8.dp)
                ) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        itemsIndexed(pages) { index, page ->
                            val isSelected = index == selectedIndex
                            Box(
                                modifier = Modifier
                                    .size(56.dp, 76.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .padding(if (isSelected) 2.5.dp else 0.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.selectPage(index) }
                                    .testTag("thumbnail_page_$index")
                            ) {
                                val imagePath = page.processedImagePath ?: page.originalImagePath
                                AsyncImage(
                                    model = File(imagePath),
                                    contentDescription = "Page ${index + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Page Index Badge
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(4.dp)
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.7f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Add page button
                        item {
                            Box(
                                modifier = Modifier
                                    .size(56.dp, 76.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                                    .clickable {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Add page",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (pages.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { showExportSheet = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("export_pdf_fab")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export PDF", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { padding ->
        if (pages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No pages added yet", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onAddFromCamera) {
                        Text("Open Camera")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Large Document Page Preview
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .height(380.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentPage != null) {
                            val displayPath = currentPage.processedImagePath ?: currentPage.originalImagePath
                            AsyncImage(
                                model = File(displayPath),
                                contentDescription = "Page Preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                            )
                        }
                    }
                }

                // Enhancement Filters Selector Row
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Enhancement Filter",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "All pages",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Switch(
                                checked = applyFilterToAll,
                                onCheckedChange = { applyFilterToAll = it }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(EnhancementMode.entries) { _, mode ->
                            val isSelected = currentPage?.enhancementMode == mode
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    viewModel.setEnhancementMode(context, mode, applyFilterToAll)
                                },
                                label = { Text(mode.displayName) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Filled.Done, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Page Actions Toolbar
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rotate CCW
                        IconButton(onClick = { viewModel.rotateCurrentPage(context, clockwise = false) }) {
                            Icon(Icons.Filled.RotateLeft, contentDescription = "Rotate CCW")
                        }

                        // Rotate CW
                        IconButton(onClick = { viewModel.rotateCurrentPage(context, clockwise = true) }) {
                            Icon(Icons.Filled.RotateRight, contentDescription = "Rotate CW")
                        }

                        // Crop
                        IconButton(onClick = { showCropDialog = true }) {
                            Icon(Icons.Filled.Crop, contentDescription = "Crop page")
                        }

                        // Fine tune (Brightness / Contrast)
                        IconButton(onClick = { showTuneSheet = true }) {
                            Icon(Icons.Filled.Tune, contentDescription = "Fine-tune contrast")
                        }

                        // Duplicate page
                        IconButton(onClick = {
                            currentPage?.let { viewModel.duplicatePage(it.id) }
                        }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate page")
                        }

                        // Move page left
                        IconButton(
                            onClick = {
                                if (selectedIndex > 0) viewModel.movePage(selectedIndex, selectedIndex - 1)
                            },
                            enabled = selectedIndex > 0
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Move page left")
                        }

                        // Move page right
                        IconButton(
                            onClick = {
                                if (selectedIndex < pages.size - 1) viewModel.movePage(selectedIndex, selectedIndex + 1)
                            },
                            enabled = selectedIndex < pages.size - 1
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Move page right")
                        }

                        // Delete page
                        IconButton(
                            onClick = {
                                currentPage?.let { viewModel.removePage(it.id) }
                            }
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete page", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(80.dp)) // padding for FAB
            }
        }
    }

    // Brightness & Contrast Adjustment Sheet
    if (showTuneSheet && currentPage != null) {
        ModalBottomSheet(
            onDismissRequest = { showTuneSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            var brightness by remember { mutableFloatStateOf(currentPage.brightness) }
            var contrast by remember { mutableFloatStateOf(currentPage.contrast) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Adjust Lighting & Contrast",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text("Brightness: ${brightness.toInt()}", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = brightness,
                    onValueChange = {
                        brightness = it
                        viewModel.updateBrightnessContrast(context, brightness, contrast)
                    },
                    valueRange = -80f..80f
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text("Contrast: ${String.format(java.util.Locale.US, "%.2fx", contrast)}", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = contrast,
                    onValueChange = {
                        contrast = it
                        viewModel.updateBrightnessContrast(context, brightness, contrast)
                    },
                    valueRange = 0.6f..2.2f
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { showTuneSheet = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Apply")
                }
            }
        }
    }

    // Interactive Crop Dialog
    if (showCropDialog && currentPage != null) {
        var cropLeft by remember { mutableFloatStateOf(currentPage.cropLeft) }
        var cropTop by remember { mutableFloatStateOf(currentPage.cropTop) }
        var cropRight by remember { mutableFloatStateOf(currentPage.cropRight) }
        var cropBottom by remember { mutableFloatStateOf(currentPage.cropBottom) }

        AlertDialog(
            onDismissRequest = { showCropDialog = false },
            title = { Text("Crop Page") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Top Margin: ${(cropTop * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = cropTop,
                        onValueChange = { cropTop = it.coerceAtMost(cropBottom - 0.1f) },
                        valueRange = 0f..0.8f
                    )

                    Text("Bottom Margin: ${((1f - cropBottom) * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = cropBottom,
                        onValueChange = { cropBottom = it.coerceAtLeast(cropTop + 0.1f) },
                        valueRange = 0.2f..1f
                    )

                    Text("Left Margin: ${(cropLeft * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = cropLeft,
                        onValueChange = { cropLeft = it.coerceAtMost(cropRight - 0.1f) },
                        valueRange = 0f..0.8f
                    )

                    Text("Right Margin: ${((1f - cropRight) * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = cropRight,
                        onValueChange = { cropRight = it.coerceAtLeast(cropLeft + 0.1f) },
                        valueRange = 0.2f..1f
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateCrop(context, cropLeft, cropTop, cropRight, cropBottom)
                    showCropDialog = false
                }) {
                    Text("Save Crop")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCropDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Discard Confirmation Dialog
    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("Discard Scanning Session?") },
            text = { Text("Leaving now will discard the ${pages.size} captured pages.") },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirm = false
                        viewModel.clearSession()
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) {
                    Text("Keep Editing")
                }
            }
        )
    }

    // PDF Export Bottom Sheet
    if (showExportSheet) {
        PdfExportSheet(
            pageCount = pages.size,
            generationState = generationState,
            onDismiss = {
                viewModel.resetGenerationState()
                showExportSheet = false
            },
            onGenerate = { title, pageSize, orient, quality, category, isProtected, pwd ->
                viewModel.generatePdf(
                    context = context,
                    title = title,
                    pageSize = pageSize,
                    orientation = orient,
                    quality = quality,
                    category = category,
                    isPasswordProtected = isProtected,
                    password = pwd
                )
            },
            onOpenGenerated = { doc ->
                showExportSheet = false
                viewModel.clearSession()
                onOpenGeneratedDoc(doc)
            },
            onShareGenerated = { doc ->
                onShareGeneratedDoc(doc)
            },
            onDone = {
                showExportSheet = false
                viewModel.clearSession()
                onDoneNavHome()
            }
        )
    }
}
