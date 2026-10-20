package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.ui.theme.DarkCardGradient
import com.example.ui.theme.DarkGradientBlack
import com.example.ui.theme.LightCardGradient
import com.example.ui.theme.LightGradientWhite
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CompressionQuality
import com.example.data.model.DocumentEntity
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.DocumentItemCard
import com.example.ui.components.EmptyDocumentsState
import com.example.ui.components.RenameDialog
import com.example.ui.components.StorageOverviewCard
import com.example.viewmodel.DocumentsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    documentsViewModel: DocumentsViewModel,
    onNavigateToScan: () -> Unit,
    onImportGallery: (List<Uri>) -> Unit,
    onNavigateToDocuments: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToCombine: () -> Unit,
    onNavigateToCompress: () -> Unit,
    onOpenDocument: (DocumentEntity) -> Unit
) {
    val context = LocalContext.current
    val documents by documentsViewModel.documents.collectAsStateWithLifecycle()
    val storageStats by documentsViewModel.storageStats.collectAsStateWithLifecycle()

    var docToRename by remember { mutableStateOf<DocumentEntity?>(null) }
    var docToDelete by remember { mutableStateOf<DocumentEntity?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            onImportGallery(uris)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DocumentScanner,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Clarity PDF",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToDocuments,
                        modifier = Modifier.testTag("home_search_button")
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = "Search Documents")
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("home_settings_button")
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Hero Scanning Card (Pixel-inspired prominent container)
            item {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToScan() }
                        .testTag("hero_scan_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Scan document",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Capture pages and turn them into a clear, optimized PDF.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CameraAlt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onNavigateToScan,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Start Scanner")
                            }

                            Button(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Filled.PhotoLibrary,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Import Images", color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }

            // Quick Actions Strip
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionPill(
                        icon = Icons.Filled.Merge,
                        label = "Combine PDFs",
                        onClick = onNavigateToCombine,
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionPill(
                        icon = Icons.Filled.Tune,
                        label = "Compress PDF",
                        onClick = onNavigateToCompress,
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionPill(
                        icon = Icons.Filled.Folder,
                        label = "All Files",
                        onClick = onNavigateToDocuments,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Storage Summary
            item {
                StorageOverviewCard(
                    docCount = storageStats.first,
                    totalBytes = storageStats.second
                )
            }

            // Recent Documents Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Documents",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (documents.isNotEmpty()) {
                        TextButton(onClick = onNavigateToDocuments) {
                            Text("See all (${documents.size})")
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Recent documents list or thoughtful empty state
            if (documents.isEmpty()) {
                item {
                    EmptyDocumentsState(onScanClick = onNavigateToScan)
                }
            } else {
                items(documents.take(5), key = { it.id }) { doc ->
                    DocumentItemCard(
                        document = doc,
                        onClick = { onOpenDocument(doc) },
                        onOpen = { onOpenDocument(doc) },
                        onShare = { documentsViewModel.shareDocument(doc, context) },
                        onShareWhatsApp = { documentsViewModel.shareDocumentToWhatsApp(doc, context) },
                        onRename = { docToRename = doc },
                        onDuplicate = { documentsViewModel.duplicateDocument(doc) },
                        onCompress = { documentsViewModel.compressDocument(doc, CompressionQuality.STANDARD, context) },
                        onDelete = { docToDelete = doc }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    // Dialogs
    docToRename?.let { doc ->
        RenameDialog(
            initialTitle = doc.title,
            onDismiss = { docToRename = null },
            onConfirm = { newName ->
                documentsViewModel.renameDocument(doc, newName)
                docToRename = null
            }
        )
    }

    docToDelete?.let { doc ->
        DeleteConfirmDialog(
            itemCount = 1,
            onDismiss = { docToDelete = null },
            onConfirm = {
                documentsViewModel.deleteDocument(doc)
                docToDelete = null
            }
        )
    }
}

@Composable
fun QuickActionPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val pillShape = RoundedCornerShape(16.dp)
    Card(
        shape = pillShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)),
        modifier = modifier
            .clip(pillShape)
            .background(if (isDark) DarkGradientBlack else LightGradientWhite, pillShape)
            .clickable { onClick() }
            .testTag("quick_action_${label.lowercase().replace(" ", "_")}")
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 14.dp, horizontal = 8.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}
