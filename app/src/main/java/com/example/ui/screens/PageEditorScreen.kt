package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.DocumentEntity
import com.example.data.model.EnhancementMode
import com.example.ui.components.LiveCropDialog
import com.example.ui.components.SortPagesSheet
import com.example.ui.theme.DarkCardGradient
import com.example.ui.theme.DarkGradientBlack
import com.example.ui.theme.LightCardGradient
import com.example.ui.theme.LightGradientWhite
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
    onShareWhatsAppDoc: (DocumentEntity) -> Unit = {},
    onDoneNavHome: () -> Unit
) {
    val context = LocalContext.current
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val selectedIndex by viewModel.selectedPageIndex.collectAsStateWithLifecycle()
    val generationState by viewModel.generationState.collectAsStateWithLifecycle()
    val isDark = isSystemInDarkTheme()

    var showExportSheet by rememberSaveable { mutableStateOf(false) }
    var showTuneSheet by rememberSaveable { mutableStateOf(false) }
    var showCropDialog by rememberSaveable { mutableStateOf(false) }
    var showSortSheet by rememberSaveable { mutableStateOf(false) }
    var showExitConfirm by rememberSaveable { mutableStateOf(false) }
    var applyFilterToAll by rememberSaveable { mutableStateOf(false) }

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
                            text = "Clarity Studio",
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
            // Filmstrip carousel of pages with Sort / Reorder access
            Surface(
                tonalElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(vertical = 6.dp)
                ) {
                    // Header row with Sort pages action
                    if (pages.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Thumbnails (${pages.size})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showSortSheet = true }
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SwapHoriz,
                                    contentDescription = "Sort Pages",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Sort Pages",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

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
                                    .size(58.dp, 78.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                                    )
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .padding(if (isSelected) 2.dp else 0.dp)
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
                                        .background(Color.Black.copy(alpha = 0.75f)),
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
                                    .size(58.dp, 78.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF8FAFC))
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
                val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                val cardHeight = if (isLandscape) 240.dp else 380.dp

                var dragAccumulator by remember { mutableFloatStateOf(0f) }

                val previewCardShape = RoundedCornerShape(20.dp)
                Card(
                    shape = previewCardShape,
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = if (isLandscape) 8.dp else 14.dp)
                        .fillMaxWidth()
                        .height(cardHeight)
                        .clip(previewCardShape)
                        .background(
                            if (isDark) DarkCardGradient else LightCardGradient,
                            previewCardShape
                        )
                        .pointerInput(selectedIndex, pages.size) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (dragAccumulator < -60f && selectedIndex < pages.size - 1) {
                                        viewModel.selectPage(selectedIndex + 1)
                                    } else if (dragAccumulator > 60f && selectedIndex > 0) {
                                        viewModel.selectPage(selectedIndex - 1)
                                    }
                                    dragAccumulator = 0f
                                },
                                onDragCancel = { dragAccumulator = 0f },
                                onHorizontalDrag = { _, dragAmount ->
                                    dragAccumulator += dragAmount
                                }
                            )
                        }
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

                        // Arrow Navigation: Left / Previous Page
                        if (selectedIndex > 0) {
                            IconButton(
                                onClick = { viewModel.selectPage(selectedIndex - 1) },
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 8.dp)
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(if (isDark) Color(0xFF0F172A).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.9f))
                                    .border(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1), CircleShape)
                                    .testTag("preview_arrow_prev_page")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Previous Page",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Arrow Navigation: Right / Next Page
                        if (selectedIndex < pages.size - 1) {
                            IconButton(
                                onClick = { viewModel.selectPage(selectedIndex + 1) },
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 8.dp)
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(if (isDark) Color(0xFF0F172A).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.9f))
                                    .border(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1), CircleShape)
                                    .testTag("preview_arrow_next_page")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Next Page",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Page Counter Indicator Badge
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isDark) Color(0xFF0F172A).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.92f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "${selectedIndex + 1} / ${pages.size}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Enhancement Filters Selector Row
                val filterCardShape = RoundedCornerShape(18.dp)
                Card(
                    shape = filterCardShape,
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(filterCardShape)
                        .background(
                            if (isDark) DarkGradientBlack else LightGradientWhite,
                            filterCardShape
                        )
                        .padding(14.dp)
                ) {
                    Column {
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

                        Spacer(modifier = Modifier.height(8.dp))

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
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Page Actions Toolbar (Fixed Arrow Navigation & Quick Actions)
                val toolbarShape = RoundedCornerShape(18.dp)
                Surface(
                    shape = toolbarShape,
                    color = Color.Transparent,
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp,
                    border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(toolbarShape)
                        .background(
                            if (isDark) DarkGradientBlack else LightGradientWhite,
                            toolbarShape
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous page navigation
                        IconButton(
                            onClick = {
                                if (selectedIndex > 0) viewModel.selectPage(selectedIndex - 1)
                            },
                            enabled = selectedIndex > 0
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous page")
                        }

                        // Next page navigation
                        IconButton(
                            onClick = {
                                if (selectedIndex < pages.size - 1) viewModel.selectPage(selectedIndex + 1)
                            },
                            enabled = selectedIndex < pages.size - 1
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next page")
                        }

                        // Rotate CCW
                        IconButton(onClick = { viewModel.rotateCurrentPage(context, clockwise = false) }) {
                            Icon(Icons.Filled.RotateLeft, contentDescription = "Rotate CCW")
                        }

                        // Rotate CW
                        IconButton(onClick = { viewModel.rotateCurrentPage(context, clockwise = true) }) {
                            Icon(Icons.Filled.RotateRight, contentDescription = "Rotate CW")
                        }

                        // Crop (Live Preview Dialog)
                        IconButton(onClick = { showCropDialog = true }) {
                            Icon(Icons.Filled.Crop, contentDescription = "Crop page")
                        }

                        // Fine tune (Live Preview Sheet)
                        IconButton(onClick = { showTuneSheet = true }) {
                            Icon(Icons.Filled.Tune, contentDescription = "Fine-tune lighting")
                        }

                        // Reorder / Sort pages
                        IconButton(onClick = { showSortSheet = true }) {
                            Icon(Icons.Filled.SwapHoriz, contentDescription = "Sort pages")
                        }

                        // Duplicate page
                        IconButton(onClick = {
                            currentPage?.let { viewModel.duplicatePage(it.id) }
                        }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate page")
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

    // Live Brightness & Contrast Adjustment Sheet
    if (showTuneSheet && currentPage != null) {
        ModalBottomSheet(
            onDismissRequest = { showTuneSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            var brightness by remember { mutableFloatStateOf(currentPage.brightness) }
            var contrast by remember { mutableFloatStateOf(currentPage.contrast) }

            // Live ColorMatrix calculation for zero-latency 60fps preview
            val liveColorFilter = remember(brightness, contrast) {
                val c = contrast
                ColorFilter.colorMatrix(
                    ColorMatrix(
                        floatArrayOf(
                            c, 0f, 0f, 0f, brightness,
                            0f, c, 0f, 0f, brightness,
                            0f, 0f, c, 0f, brightness,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Adjust Lighting & Contrast",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = {
                        brightness = 0f
                        contrast = 1f
                    }) {
                        Text("Reset")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Live Image Preview Card
                val livePreviewShape = RoundedCornerShape(16.dp)
                Card(
                    shape = livePreviewShape,
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(livePreviewShape)
                        .background(
                            if (isDark) DarkCardGradient else LightCardGradient,
                            livePreviewShape
                        )
                        .padding(6.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        val displayPath = currentPage.processedImagePath ?: currentPage.originalImagePath
                        AsyncImage(
                            model = File(displayPath),
                            contentDescription = "Live Preview Adjustment",
                            contentScale = ContentScale.Fit,
                            colorFilter = liveColorFilter,
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                        ) {
                            Text(
                                "Live Preview",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = brightness == 0f && contrast == 1f,
                        onClick = { brightness = 0f; contrast = 1f },
                        label = { Text("Normal") }
                    )
                    FilterChip(
                        selected = brightness == 15f && contrast == 1.3f,
                        onClick = { brightness = 15f; contrast = 1.3f },
                        label = { Text("High Contrast") }
                    )
                    FilterChip(
                        selected = brightness == 25f && contrast == 1.15f,
                        onClick = { brightness = 25f; contrast = 1.15f },
                        label = { Text("Brighten") }
                    )
                    FilterChip(
                        selected = brightness == -15f && contrast == 1.35f,
                        onClick = { brightness = -15f; contrast = 1.35f },
                        label = { Text("Deep Text") }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Brightness", style = MaterialTheme.typography.labelLarge)
                    Text("${brightness.toInt()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = brightness,
                    onValueChange = { brightness = it },
                    valueRange = -80f..80f
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Contrast", style = MaterialTheme.typography.labelLarge)
                    Text(
                        String.format(java.util.Locale.US, "%.2fx", contrast),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = contrast,
                    onValueChange = { contrast = it },
                    valueRange = 0.6f..2.2f
                )

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        viewModel.updateBrightnessContrast(context, brightness, contrast)
                        showTuneSheet = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Apply Adjustments", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    // Interactive Live Crop Dialog
    if (showCropDialog && currentPage != null) {
        LiveCropDialog(
            page = currentPage,
            onDismiss = { showCropDialog = false },
            onSaveCrop = { left, top, right, bottom ->
                viewModel.updateCrop(context, left, top, right, bottom)
                showCropDialog = false
            }
        )
    }

    // Sort & Reorder Pages Bottom Sheet
    if (showSortSheet) {
        SortPagesSheet(
            pages = pages,
            selectedIndex = selectedIndex,
            onMovePage = { from, to -> viewModel.movePage(from, to) },
            onReverseOrder = { viewModel.reversePageOrder() },
            onSelectPage = { viewModel.selectPage(it) },
            onDismiss = { showSortSheet = false }
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
            onShareWhatsApp = { doc ->
                onShareWhatsAppDoc(doc)
            },
            onDone = {
                showExportSheet = false
                viewModel.clearSession()
                onDoneNavHome()
            }
        )
    }
}
