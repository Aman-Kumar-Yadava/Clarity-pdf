package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val filePath: String,
    val pageCount: Int,
    val fileSizeBytes: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val pageSize: String = "A4",
    val orientation: String = "PORTRAIT",
    val compressionQuality: String = "HIGH",
    val thumbnailPath: String? = null,
    val isPasswordProtected: Boolean = false,
    val category: String = "General"
)
