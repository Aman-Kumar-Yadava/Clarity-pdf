package com.example.ui.navigation

import com.example.data.model.DocumentEntity

sealed interface Screen {
    data object Home : Screen
    data object Documents : Screen
    data object Settings : Screen
    data object Scanner : Screen
    data object Editor : Screen
    data class PdfView(val document: DocumentEntity) : Screen
    data object Combine : Screen
    data object Compress : Screen
}
