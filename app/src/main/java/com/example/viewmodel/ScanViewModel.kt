package com.example.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CompressionQuality
import com.example.data.model.DocumentEntity
import com.example.data.model.EnhancementMode
import com.example.data.model.PdfOrientation
import com.example.data.model.PdfPageSize
import com.example.data.model.ScanPage
import com.example.data.repository.DocumentRepository
import com.example.engine.DocumentEnhancer
import com.example.engine.PdfExportConfig
import com.example.engine.PdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface GenerationUiState {
    data object Idle : GenerationUiState
    data class Processing(val current: Int, val total: Int, val message: String) : GenerationUiState
    data class Success(val document: DocumentEntity) : GenerationUiState
    data class Error(val message: String) : GenerationUiState
}

class ScanViewModel(
    private val repository: DocumentRepository
) : ViewModel() {

    private val _pages = MutableStateFlow<List<ScanPage>>(emptyList())
    val pages: StateFlow<List<ScanPage>> = _pages.asStateFlow()

    private val _selectedPageIndex = MutableStateFlow(0)
    val selectedPageIndex: StateFlow<Int> = _selectedPageIndex.asStateFlow()

    private val _generationState = MutableStateFlow<GenerationUiState>(GenerationUiState.Idle)
    val generationState: StateFlow<GenerationUiState> = _generationState.asStateFlow()

    private val _isProcessingThumbnails = MutableStateFlow(false)
    val isProcessingThumbnails: StateFlow<Boolean> = _isProcessingThumbnails.asStateFlow()

    fun selectPage(index: Int) {
        if (index in _pages.value.indices) {
            _selectedPageIndex.value = index
        }
    }

    /**
     * Imports images from gallery URIs, copying them to app cache safely.
     */
    fun importImages(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isProcessingThumbnails.value = true
            val cacheDir = File(context.cacheDir, "scan_raw").apply { mkdirs() }
            val newPages = mutableListOf<ScanPage>()

            withContext(Dispatchers.IO) {
                for (uri in uris) {
                    try {
                        val file = File(cacheDir, "raw_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(6)}.jpg")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(file).use { out ->
                                input.copyTo(out)
                            }
                        }
                        if (file.exists() && file.length() > 0) {
                            newPages.add(ScanPage(originalImagePath = file.absolutePath))
                        }
                    } catch (_: Exception) {}
                }
            }

            _pages.value = _pages.value + newPages
            if (_selectedPageIndex.value >= _pages.value.size) {
                _selectedPageIndex.value = 0
            }
            _isProcessingThumbnails.value = false
        }
    }

    /**
     * Adds an image captured from the Camera.
     */
    fun addCapturedImage(filePath: String) {
        val newPage = ScanPage(originalImagePath = filePath)
        _pages.value = _pages.value + newPage
        _selectedPageIndex.value = _pages.value.lastIndex
    }

    fun removePage(pageId: String) {
        val currentList = _pages.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == pageId }
        if (index != -1) {
            currentList.removeAt(index)
            _pages.value = currentList
            _selectedPageIndex.value = _selectedPageIndex.value.coerceIn(0, maxOf(0, currentList.size - 1))
        }
    }

    fun duplicatePage(pageId: String) {
        val currentList = _pages.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == pageId }
        if (index != -1) {
            val original = currentList[index]
            val duplicate = original.copy(
                id = java.util.UUID.randomUUID().toString(),
                processedImagePath = null
            )
            currentList.add(index + 1, duplicate)
            _pages.value = currentList
            _selectedPageIndex.value = index + 1
        }
    }

    fun movePage(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in _pages.value.indices || toIndex !in _pages.value.indices) return
        val currentList = _pages.value.toMutableList()
        val item = currentList.removeAt(fromIndex)
        currentList.add(toIndex, item)
        _pages.value = currentList
        _selectedPageIndex.value = toIndex
    }

    fun rotateCurrentPage(context: Context, clockwise: Boolean = true) {
        val index = _selectedPageIndex.value
        if (index !in _pages.value.indices) return
        val page = _pages.value[index]
        val delta = if (clockwise) 90 else 270
        val newRotation = (page.rotationDegrees + delta) % 360
        val updated = page.copy(rotationDegrees = newRotation, processedImagePath = null)
        updatePageAt(index, updated, context)
    }

    fun setEnhancementMode(context: Context, mode: EnhancementMode, applyToAll: Boolean = false) {
        if (applyToAll) {
            val updated = _pages.value.map { it.copy(enhancementMode = mode, processedImagePath = null) }
            _pages.value = updated
            refreshCurrentPreview(context)
        } else {
            val index = _selectedPageIndex.value
            if (index in _pages.value.indices) {
                val page = _pages.value[index]
                val updated = page.copy(enhancementMode = mode, processedImagePath = null)
                updatePageAt(index, updated, context)
            }
        }
    }

    fun updateBrightnessContrast(context: Context, brightness: Float, contrast: Float) {
        val index = _selectedPageIndex.value
        if (index in _pages.value.indices) {
            val page = _pages.value[index]
            val updated = page.copy(brightness = brightness, contrast = contrast, processedImagePath = null)
            updatePageAt(index, updated, context)
        }
    }

    fun updateCrop(context: Context, left: Float, top: Float, right: Float, bottom: Float) {
        val index = _selectedPageIndex.value
        if (index in _pages.value.indices) {
            val page = _pages.value[index]
            val updated = page.copy(
                cropLeft = left,
                cropTop = top,
                cropRight = right,
                cropBottom = bottom,
                processedImagePath = null
            )
            updatePageAt(index, updated, context)
        }
    }

    fun resetEdits(context: Context) {
        val index = _selectedPageIndex.value
        if (index in _pages.value.indices) {
            val page = _pages.value[index]
            val updated = page.copy(
                rotationDegrees = 0,
                enhancementMode = EnhancementMode.DOCUMENT,
                brightness = 0f,
                contrast = 1f,
                cropLeft = 0f,
                cropTop = 0f,
                cropRight = 1f,
                cropBottom = 1f,
                processedImagePath = null
            )
            updatePageAt(index, updated, context)
        }
    }

    private fun updatePageAt(index: Int, page: ScanPage, context: Context) {
        val currentList = _pages.value.toMutableList()
        currentList[index] = page
        _pages.value = currentList
        viewModelScope.launch {
            try {
                val procPath = DocumentEnhancer.processPage(context, page, isThumbnail = false)
                val refreshedList = _pages.value.toMutableList()
                if (index in refreshedList.indices) {
                    refreshedList[index] = page.copy(processedImagePath = procPath)
                    _pages.value = refreshedList
                }
            } catch (_: Exception) {}
        }
    }

    private fun refreshCurrentPreview(context: Context) {
        val index = _selectedPageIndex.value
        if (index in _pages.value.indices) {
            viewModelScope.launch {
                try {
                    val page = _pages.value[index]
                    val procPath = DocumentEnhancer.processPage(context, page, isThumbnail = false)
                    val refreshedList = _pages.value.toMutableList()
                    refreshedList[index] = page.copy(processedImagePath = procPath)
                    _pages.value = refreshedList
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Executes the real PDF generation pipeline and saves the document record to Room.
     */
    fun generatePdf(
        context: Context,
        title: String,
        pageSize: PdfPageSize,
        orientation: PdfOrientation,
        quality: CompressionQuality,
        category: String = "General",
        isPasswordProtected: Boolean = false,
        password: String = ""
    ) {
        val currentPages = _pages.value
        if (currentPages.isEmpty()) {
            _generationState.value = GenerationUiState.Error("No pages to export")
            return
        }

        val finalTitle = title.ifBlank {
            "Clarity_Doc_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        }

        viewModelScope.launch {
            _generationState.value = GenerationUiState.Processing(0, currentPages.size, "Preparing documents...")
            try {
                val config = PdfExportConfig(
                    title = finalTitle,
                    pageSize = pageSize,
                    orientation = orientation,
                    quality = quality,
                    isPasswordProtected = isPasswordProtected,
                    password = password
                )

                val pdfFile = PdfGenerator.generatePdf(context, currentPages, config) { curr, total, msg ->
                    _generationState.value = GenerationUiState.Processing(curr, total, msg)
                }

                // Render cover thumbnail
                val thumbPath = PdfGenerator.renderPdfThumbnail(context, pdfFile)

                val document = DocumentEntity(
                    title = finalTitle,
                    filePath = pdfFile.absolutePath,
                    pageCount = currentPages.size,
                    fileSizeBytes = pdfFile.length(),
                    pageSize = pageSize.displayName,
                    orientation = orientation.displayName,
                    compressionQuality = quality.displayName,
                    thumbnailPath = thumbPath,
                    isPasswordProtected = isPasswordProtected,
                    category = category
                )

                val insertedId = repository.insertDocument(document)
                val savedDocument = document.copy(id = insertedId)

                _generationState.value = GenerationUiState.Success(savedDocument)
            } catch (e: Exception) {
                _generationState.value = GenerationUiState.Error(e.message ?: "Failed to generate PDF")
            }
        }
    }

    fun resetGenerationState() {
        _generationState.value = GenerationUiState.Idle
    }

    fun clearSession() {
        _pages.value = emptyList()
        _selectedPageIndex.value = 0
        _generationState.value = GenerationUiState.Idle
    }
}
