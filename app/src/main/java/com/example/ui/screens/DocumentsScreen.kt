package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.example.viewmodel.DocumentsViewModel
import com.example.viewmodel.SortOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    documentsViewModel: DocumentsViewModel,
    onNavigateToScan: () -> Unit,
    onOpenDocument: (DocumentEntity) -> Unit
) {
    val context = LocalContext.current
    val documents by documentsViewModel.documents.collectAsStateWithLifecycle()
    val searchQuery by documentsViewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by documentsViewModel.selectedCategory.collectAsStateWithLifecycle()
    val sortOption by documentsViewModel.sortOption.collectAsStateWithLifecycle()
    val selectedDocIds by documentsViewModel.selectedDocIds.collectAsStateWithLifecycle()

    var showSortMenu by remember { mutableStateOf(false) }
    var docToRename by remember { mutableStateOf<DocumentEntity?>(null) }
    var docToDelete by remember { mutableStateOf<DocumentEntity?>(null) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    val categories = listOf("All", "Notes", "ID Cards", "Receipts", "Invoices", "Certificates", "Books", "General", "Combined")

    val isSelectionMode = selectedDocIds.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text(
                            text = "${selectedDocIds.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "Documents",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(
                            onClick = {
                                val firstDoc = documents.firstOrNull { it.id in selectedDocIds }
                                firstDoc?.let { documentsViewModel.shareDocumentToWhatsApp(it, context) }
                            }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_whatsapp),
                                contentDescription = "Share on WhatsApp",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                val firstDoc = documents.firstOrNull { it.id in selectedDocIds }
                                firstDoc?.let { documentsViewModel.shareDocument(it, context) }
                            }
                        ) {
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = "Share",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(onClick = { showBatchDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete selected", tint = MaterialTheme.colorScheme.error)
                        }
                        IconButton(onClick = { documentsViewModel.clearSelection() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear selection")
                        }
                    } else {
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Filled.Sort, contentDescription = "Sort options")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                SortOption.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = option.displayName,
                                                fontWeight = if (sortOption == option) FontWeight.Bold else FontWeight.Normal,
                                                color = if (sortOption == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            documentsViewModel.setSortOption(option)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { documentsViewModel.setSearchQuery(it) },
                placeholder = { Text("Search by document title...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { documentsViewModel.setSearchQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .testTag("document_search_input")
            )

            // Category Filter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { category ->
                    val isSelected = selectedCategory.equals(category, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = { documentsViewModel.setCategory(category) },
                        label = { Text(category) },
                        modifier = Modifier.testTag("filter_chip_$category")
                    )
                }
            }

            // Documents List
            if (documents.isEmpty()) {
                if (searchQuery.isNotBlank() || selectedCategory != "All") {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No documents found",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Try adjusting your search or category filter.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            TextButton(onClick = {
                                documentsViewModel.setSearchQuery("")
                                documentsViewModel.setCategory("All")
                            }) {
                                Text("Reset filters")
                            }
                        }
                    }
                } else {
                    EmptyDocumentsState(onScanClick = onNavigateToScan)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(documents, key = { it.id }) { doc ->
                        val isSelected = selectedDocIds.contains(doc.id)
                        DocumentItemCard(
                            document = doc,
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
                            onClick = { onOpenDocument(doc) },
                            onLongClick = { documentsViewModel.toggleDocSelection(doc.id) },
                            onOpen = { onOpenDocument(doc) },
                            onShare = { documentsViewModel.shareDocument(doc, context) },
                            onShareWhatsApp = { documentsViewModel.shareDocumentToWhatsApp(doc, context) },
                            onRename = { docToRename = doc },
                            onDuplicate = { documentsViewModel.duplicateDocument(doc) },
                            onCompress = { documentsViewModel.compressDocument(doc, CompressionQuality.STANDARD, context) },
                            onDelete = { docToDelete = doc }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(30.dp))
                    }
                }
            }
        }
    }

    // Rename Dialog
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

    // Single Delete Dialog
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

    // Batch Delete Dialog
    if (showBatchDeleteConfirm) {
        DeleteConfirmDialog(
            itemCount = selectedDocIds.size,
            onDismiss = { showBatchDeleteConfirm = false },
            onConfirm = {
                documentsViewModel.deleteSelectedDocuments()
                showBatchDeleteConfirm = false
            }
        )
    }
}
