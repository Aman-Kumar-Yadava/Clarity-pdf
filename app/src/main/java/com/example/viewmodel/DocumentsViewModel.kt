package com.example.viewmodel

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CompressionQuality
import com.example.data.model.DocumentEntity
import com.example.data.repository.DocumentRepository
import com.example.engine.PdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class SortOption(val displayName: String) {
    DATE_DESC("Newest first"),
    DATE_ASC("Oldest first"),
    NAME_ASC("Name (A–Z)"),
    NAME_DESC("Name (Z–A)"),
    SIZE_DESC("Largest size"),
    SIZE_ASC("Smallest size")
}

class DocumentsViewModel(
    private val repository: DocumentRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.DATE_DESC)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _selectedDocIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedDocIds: StateFlow<Set<Long>> = _selectedDocIds.asStateFlow()

    private val _storageStats = MutableStateFlow(Pair(0, 0L))
    val storageStats: StateFlow<Pair<Int, Long>> = _storageStats.asStateFlow()

    private val _isProcessingAction = MutableStateFlow(false)
    val isProcessingAction: StateFlow<Boolean> = _isProcessingAction.asStateFlow()

    val documents: StateFlow<List<DocumentEntity>> = combine(
        repository.allDocuments,
        _searchQuery,
        _selectedCategory,
        _sortOption
    ) { allDocs, query, category, sort ->
        var list = allDocs

        // 1. Filter by Category
        if (category != "All") {
            list = list.filter { it.category.equals(category, ignoreCase = true) }
        }

        // 2. Filter by Search Query
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            list = list.filter { it.title.lowercase().contains(q) }
        }

        // 3. Sort
        when (sort) {
            SortOption.DATE_DESC -> list.sortedByDescending { it.createdAt }
            SortOption.DATE_ASC -> list.sortedBy { it.createdAt }
            SortOption.NAME_ASC -> list.sortedBy { it.title.lowercase() }
            SortOption.NAME_DESC -> list.sortedByDescending { it.title.lowercase() }
            SortOption.SIZE_DESC -> list.sortedByDescending { it.fileSizeBytes }
            SortOption.SIZE_ASC -> list.sortedBy { it.fileSizeBytes }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        refreshStorageStats()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun toggleDocSelection(docId: Long) {
        val current = _selectedDocIds.value.toMutableSet()
        if (current.contains(docId)) {
            current.remove(docId)
        } else {
            current.add(docId)
        }
        _selectedDocIds.value = current
    }

    fun clearSelection() {
        _selectedDocIds.value = emptySet()
    }

    fun refreshStorageStats() {
        viewModelScope.launch {
            _storageStats.value = repository.getStorageStats()
        }
    }

    fun renameDocument(document: DocumentEntity, newTitle: String) {
        if (newTitle.isBlank() || newTitle == document.title) return
        viewModelScope.launch {
            repository.renameDocument(document, newTitle)
            refreshStorageStats()
        }
    }

    fun duplicateDocument(document: DocumentEntity) {
        viewModelScope.launch {
            _isProcessingAction.value = true
            try {
                repository.duplicateDocument(document)
                refreshStorageStats()
            } finally {
                _isProcessingAction.value = false
            }
        }
    }

    fun deleteDocument(document: DocumentEntity) {
        viewModelScope.launch {
            repository.deleteDocument(document)
            refreshStorageStats()
        }
    }

    fun deleteSelectedDocuments() {
        val idsToDelete = _selectedDocIds.value.toList()
        if (idsToDelete.isEmpty()) return
        viewModelScope.launch {
            val docs = documents.value.filter { it.id in idsToDelete }
            for (d in docs) {
                repository.deleteDocument(d)
            }
            clearSelection()
            refreshStorageStats()
        }
    }

    fun compressDocument(document: DocumentEntity, quality: CompressionQuality, context: Context, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _isProcessingAction.value = true
            try {
                val srcFile = File(document.filePath)
                val compressedFile = PdfGenerator.compressPdf(context, srcFile, quality)
                val thumbPath = PdfGenerator.renderPdfThumbnail(context, compressedFile)
                val oldSize = com.example.ui.components.formatFileSize(document.fileSizeBytes)
                val newSize = com.example.ui.components.formatFileSize(compressedFile.length())
                val pct = if (document.fileSizeBytes > 0) {
                    ((document.fileSizeBytes - compressedFile.length()) * 100 / document.fileSizeBytes).toInt().coerceAtLeast(0)
                } else 0

                val newDoc = DocumentEntity(
                    title = "${document.title} (Optimized)",
                    filePath = compressedFile.absolutePath,
                    pageCount = document.pageCount,
                    fileSizeBytes = compressedFile.length(),
                    pageSize = document.pageSize,
                    orientation = document.orientation,
                    compressionQuality = quality.displayName,
                    thumbnailPath = thumbPath,
                    category = document.category
                )
                repository.insertDocument(newDoc)
                refreshStorageStats()
                Toast.makeText(context, "Compressed: $oldSize → $newSize (-$pct%)", Toast.LENGTH_LONG).show()
                onComplete()
            } catch (e: Exception) {
                Toast.makeText(context, "Compression failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                _isProcessingAction.value = false
            }
        }
    }

    fun combineDocuments(documentsToCombine: List<DocumentEntity>, outputTitle: String, context: Context) {
        if (documentsToCombine.size < 2) return
        viewModelScope.launch {
            _isProcessingAction.value = true
            try {
                val files = documentsToCombine.map { File(it.filePath) }
                val combinedFile = PdfGenerator.combinePdfs(context, files, outputTitle)
                val thumbPath = PdfGenerator.renderPdfThumbnail(context, combinedFile)
                val totalPages = documentsToCombine.sumOf { it.pageCount }
                val newDoc = DocumentEntity(
                    title = outputTitle,
                    filePath = combinedFile.absolutePath,
                    pageCount = totalPages,
                    fileSizeBytes = combinedFile.length(),
                    pageSize = "Auto",
                    orientation = "Auto",
                    compressionQuality = "High",
                    thumbnailPath = thumbPath,
                    category = "Combined"
                )
                repository.insertDocument(newDoc)
                refreshStorageStats()
                clearSelection()
                Toast.makeText(context, "PDFs combined successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Combine failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                _isProcessingAction.value = false
            }
        }
    }

    fun shareDocument(document: DocumentEntity, context: Context) {
        val file = File(document.filePath)
        if (!file.exists()) {
            Toast.makeText(context, "File not found: ${document.title}", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val clipData = ClipData.newRawUri(document.title, uri)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, document.title)
                setClipData(clipData)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooserIntent = Intent.createChooser(intent, "Share ${document.title}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(chooserIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Unable to share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareDocumentToWhatsApp(document: DocumentEntity, context: Context) {
        val file = File(document.filePath)
        if (!file.exists()) {
            Toast.makeText(context, "File not found: ${document.title}", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val clipData = ClipData.newRawUri(document.title, uri)
            
            // Try standard WhatsApp
            val whatsappIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, document.title)
                setClipData(clipData)
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(whatsappIntent)
        } catch (_: Exception) {
            try {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val clipData = ClipData.newRawUri(document.title, uri)
                // Try WhatsApp Business
                val w4bIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, document.title)
                    setClipData(clipData)
                    setPackage("com.whatsapp.w4b")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(w4bIntent)
            } catch (_: Exception) {
                Toast.makeText(context, "WhatsApp not installed. Opening share menu...", Toast.LENGTH_SHORT).show()
                shareDocument(document, context)
            }
        }
    }

    fun openDocumentExternally(document: DocumentEntity, context: Context) {
        val file = File(document.filePath)
        if (!file.exists()) {
            Toast.makeText(context, "File not found: ${document.title}", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val clipData = ClipData.newRawUri(document.title, uri)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                setClipData(clipData)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooserIntent = Intent.createChooser(intent, "Open ${document.title} with").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(chooserIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "No PDF viewer app found on device", Toast.LENGTH_SHORT).show()
        }
    }
}
