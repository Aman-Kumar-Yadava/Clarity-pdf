package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.AppDatabase
import com.example.data.model.DocumentEntity
import com.example.data.repository.DocumentRepository
import com.example.ui.screens.CameraScannerScreen
import com.example.ui.screens.CombinePdfScreen
import com.example.ui.screens.CompressPdfScreen
import com.example.ui.screens.DocumentsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PageEditorScreen
import com.example.ui.screens.PdfViewScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.ClarityPdfTheme
import com.example.viewmodel.DocumentsViewModel
import com.example.viewmodel.ScanViewModel
import java.io.File

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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ClarityPdfTheme {
                val context = LocalContext.current
                val database = remember { AppDatabase.getInstance(context) }
                val repository = remember { DocumentRepository(database.documentDao(), context) }
                val scanViewModel = remember { ScanViewModel(repository) }
                val documentsViewModel = remember { DocumentsViewModel(repository) }

                val navBackStack = remember { mutableStateListOf<Screen>(Screen.Home) }
                val currentScreen = navBackStack.lastOrNull() ?: Screen.Home

                fun navigateTo(screen: Screen) {
                    if (screen is Screen.Home || screen is Screen.Documents || screen is Screen.Settings) {
                        navBackStack.clear()
                        navBackStack.add(screen)
                    } else {
                        navBackStack.add(screen)
                    }
                }

                fun navigateBack() {
                    if (navBackStack.size > 1) {
                        navBackStack.removeAt(navBackStack.lastIndex)
                    } else if (currentScreen != Screen.Home) {
                        navBackStack.clear()
                        navBackStack.add(Screen.Home)
                    }
                }

                val showBottomNav = currentScreen is Screen.Home ||
                        currentScreen is Screen.Documents

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (showBottomNav) {
                            NavigationBar(
                                modifier = Modifier
                                    .navigationBarsPadding()
                                    .testTag("main_bottom_nav_bar")
                            ) {
                                NavigationBarItem(
                                    selected = currentScreen is Screen.Home,
                                    onClick = { navigateTo(Screen.Home) },
                                    icon = {
                                        Icon(
                                            if (currentScreen is Screen.Home) Icons.Filled.Home else Icons.Outlined.Home,
                                            contentDescription = "Home"
                                        )
                                    },
                                    label = { Text("Home") },
                                    modifier = Modifier.testTag("nav_item_home")
                                )
                                NavigationBarItem(
                                    selected = currentScreen is Screen.Documents,
                                    onClick = { navigateTo(Screen.Documents) },
                                    icon = {
                                        Icon(
                                            if (currentScreen is Screen.Documents) Icons.Filled.Folder else Icons.Outlined.Folder,
                                            contentDescription = "Documents"
                                        )
                                    },
                                    label = { Text("Documents") },
                                    modifier = Modifier.testTag("nav_item_documents")
                                )
                            }
                        }
                    },
                    floatingActionButton = {
                        if (showBottomNav) {
                            FloatingActionButton(
                                onClick = { navigateTo(Screen.Scanner) },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.testTag("main_scanner_fab")
                            ) {
                                Icon(Icons.Filled.CameraAlt, contentDescription = "Scan Document")
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "screen_transition"
                        ) { screen ->
                            when (screen) {
                                is Screen.Home -> {
                                    HomeScreen(
                                        documentsViewModel = documentsViewModel,
                                        onNavigateToScan = { navigateTo(Screen.Scanner) },
                                        onImportGallery = { uris ->
                                            scanViewModel.importImages(context, uris)
                                            navigateTo(Screen.Editor)
                                        },
                                        onNavigateToDocuments = { navigateTo(Screen.Documents) },
                                        onNavigateToSettings = { navigateTo(Screen.Settings) },
                                        onNavigateToCombine = { navigateTo(Screen.Combine) },
                                        onNavigateToCompress = { navigateTo(Screen.Compress) },
                                        onOpenDocument = { doc -> navigateTo(Screen.PdfView(doc)) }
                                    )
                                }

                                is Screen.Documents -> {
                                    BackHandler { navigateTo(Screen.Home) }
                                    DocumentsScreen(
                                        documentsViewModel = documentsViewModel,
                                        onNavigateToScan = { navigateTo(Screen.Scanner) },
                                        onOpenDocument = { doc -> navigateTo(Screen.PdfView(doc)) }
                                    )
                                }

                                is Screen.Settings -> {
                                    BackHandler { navigateBack() }
                                    SettingsScreen(
                                        documentsViewModel = documentsViewModel,
                                        onBack = { navigateBack() }
                                    )
                                }

                                is Screen.Scanner -> {
                                    BackHandler { navigateBack() }
                                    val scanPages by scanViewModel.pages.collectAsStateWithLifecycle()
                                    val latestThumb = scanPages.lastOrNull()?.let {
                                        it.processedImagePath ?: it.originalImagePath
                                    }

                                    CameraScannerScreen(
                                        pageCount = scanPages.size,
                                        latestThumbnailPath = latestThumb,
                                        onImageCaptured = { path ->
                                            scanViewModel.addCapturedImage(path)
                                        },
                                        onImportGallery = {
                                            navigateTo(Screen.Editor)
                                        },
                                        onFinish = {
                                            navigateTo(Screen.Editor)
                                        },
                                        onCancel = {
                                            navigateBack()
                                        }
                                    )
                                }

                                is Screen.Editor -> {
                                    BackHandler { navigateBack() }
                                    PageEditorScreen(
                                        viewModel = scanViewModel,
                                        onBack = { navigateBack() },
                                        onAddFromCamera = { navigateTo(Screen.Scanner) },
                                        onOpenGeneratedDoc = { doc ->
                                            navBackStack.clear()
                                            navBackStack.add(Screen.Home)
                                            navBackStack.add(Screen.PdfView(doc))
                                        },
                                        onShareGeneratedDoc = { doc ->
                                            documentsViewModel.shareDocument(doc, context)
                                        },
                                        onDoneNavHome = {
                                            navBackStack.clear()
                                            navBackStack.add(Screen.Home)
                                        }
                                    )
                                }

                                is Screen.PdfView -> {
                                    BackHandler { navigateBack() }
                                    PdfViewScreen(
                                        document = screen.document,
                                        onBack = { navigateBack() },
                                        onShare = { documentsViewModel.shareDocument(screen.document, context) },
                                        onOpenExternal = { documentsViewModel.openDocumentExternally(screen.document, context) }
                                    )
                                }

                                is Screen.Combine -> {
                                    BackHandler { navigateBack() }
                                    CombinePdfScreen(
                                        documentsViewModel = documentsViewModel,
                                        onBack = { navigateBack() }
                                    )
                                }

                                is Screen.Compress -> {
                                    BackHandler { navigateBack() }
                                    CompressPdfScreen(
                                        documentsViewModel = documentsViewModel,
                                        onBack = { navigateBack() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
