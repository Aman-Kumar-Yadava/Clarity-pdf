package com.example.data.repository

import android.content.Context
import com.example.data.db.DocumentDao
import com.example.data.model.DocumentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class DocumentRepository(
    private val documentDao: DocumentDao,
    private val context: Context
) {
    val allDocuments: Flow<List<DocumentEntity>> = documentDao.getAllDocuments()

    fun searchDocuments(query: String): Flow<List<DocumentEntity>> {
        return if (query.isBlank()) {
            documentDao.getAllDocuments()
        } else {
            documentDao.searchDocuments(query.trim())
        }
    }

    fun getDocumentsByCategory(category: String): Flow<List<DocumentEntity>> {
        return if (category == "All" || category.isBlank()) {
            documentDao.getAllDocuments()
        } else {
            documentDao.getDocumentsByCategory(category)
        }
    }

    fun getDocumentById(id: Long): Flow<DocumentEntity?> = documentDao.getDocumentById(id)

    suspend fun insertDocument(document: DocumentEntity): Long = withContext(Dispatchers.IO) {
        documentDao.insertDocument(document)
    }

    suspend fun updateDocument(document: DocumentEntity) = withContext(Dispatchers.IO) {
        documentDao.updateDocument(document)
    }

    suspend fun deleteDocument(document: DocumentEntity) = withContext(Dispatchers.IO) {
        // Remove files on disk
        try {
            val pdfFile = File(document.filePath)
            if (pdfFile.exists()) {
                pdfFile.delete()
            }
            document.thumbnailPath?.let { thumbPath ->
                val thumbFile = File(thumbPath)
                if (thumbFile.exists()) {
                    thumbFile.delete()
                }
            }
        } catch (_: Exception) {}
        documentDao.deleteDocument(document)
    }

    suspend fun renameDocument(document: DocumentEntity, newTitle: String): DocumentEntity = withContext(Dispatchers.IO) {
        val sanitizedTitle = newTitle.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val oldFile = File(document.filePath)
        val parentDir = oldFile.parentFile ?: context.filesDir
        val newFile = File(parentDir, "${sanitizedTitle}.pdf")
        
        var targetFile = newFile
        if (oldFile.exists() && oldFile.absolutePath != newFile.absolutePath) {
            if (newFile.exists()) {
                targetFile = File(parentDir, "${sanitizedTitle}_${System.currentTimeMillis()}.pdf")
            }
            oldFile.renameTo(targetFile)
        }

        val updated = document.copy(
            title = sanitizedTitle,
            filePath = targetFile.absolutePath,
            modifiedAt = System.currentTimeMillis()
        )
        documentDao.updateDocument(updated)
        updated
    }

    suspend fun duplicateDocument(document: DocumentEntity): DocumentEntity = withContext(Dispatchers.IO) {
        val srcFile = File(document.filePath)
        val copyTitle = "${document.title} (Copy)"
        val destFile = File(srcFile.parentFile ?: context.filesDir, "${copyTitle}_${System.currentTimeMillis()}.pdf")
        
        if (srcFile.exists()) {
            FileInputStream(srcFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
        }

        var copyThumbPath: String? = null
        document.thumbnailPath?.let { thumbPath ->
            val srcThumb = File(thumbPath)
            if (srcThumb.exists()) {
                val destThumb = File(srcThumb.parentFile ?: context.cacheDir, "thumb_${System.currentTimeMillis()}.jpg")
                FileInputStream(srcThumb).use { input ->
                    FileOutputStream(destThumb).use { output ->
                        input.copyTo(output)
                    }
                }
                copyThumbPath = destThumb.absolutePath
            }
        }

        val copyDoc = document.copy(
            id = 0,
            title = copyTitle,
            filePath = destFile.absolutePath,
            thumbnailPath = copyThumbPath,
            fileSizeBytes = if (destFile.exists()) destFile.length() else document.fileSizeBytes,
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis()
        )
        val newId = documentDao.insertDocument(copyDoc)
        copyDoc.copy(id = newId)
    }

    suspend fun getStorageStats(): Pair<Int, Long> = withContext(Dispatchers.IO) {
        val count = documentDao.getDocumentCount()
        val totalBytes = documentDao.getTotalStorageBytes() ?: 0L
        Pair(count, totalBytes)
    }
}
