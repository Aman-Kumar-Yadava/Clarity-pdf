package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import java.io.File

@Composable
fun CameraScannerScreen(
    pageCount: Int,
    latestThumbnailPath: String?,
    onImageCaptured: (filePath: String) -> Unit,
    onImportGallery: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        // Fallback view when camera permission is not granted
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Videocam,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Camera Permission Required",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Clarity PDF uses the camera to scan documents directly with live edge detection.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF94A3B8),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Grant Permission")
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onImportGallery,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                ) {
                    Text("Import from Gallery instead")
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                ) {
                    Text("Cancel", color = Color.White)
                }
            }
        }
        return
    }

    // Camera state
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var camera: Camera? by remember { mutableStateOf(null) }
    var isShutterPressed by remember { mutableStateOf(false) }

    val shutterScale by animateFloatAsState(
        targetValue = if (isShutterPressed) 0.85f else 1.0f,
        animationSpec = tween(durationMillis = 100),
        label = "shutter_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // CameraX Live Preview View
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setFlashMode(flashMode)
                        .build()
                    imageCapture = capture

                    val cameraSelector = CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build()

                    try {
                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            capture
                        )
                    } catch (_: Exception) {}
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Live Document Viewfinder Frame Overlay (Google Pixel aesthetic viewfinder)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val marginH = size.width * 0.08f
            val marginV = size.height * 0.16f
            val rectW = size.width - (marginH * 2)
            val rectH = size.height - (marginV * 2)

            // Dim outer area
            drawRect(
                color = Color.Black.copy(alpha = 0.35f),
                size = size
            )

            // Clear viewfinder window
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(marginH, marginV),
                size = Size(rectW, rectH),
                cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx())
            )

            // Viewfinder Outline & Corner Brackets
            val cornerLen = 36.dp.toPx()
            val strokeW = 4.dp.toPx()
            val bracketColor = Color(0xFF38BDF8)

            // Top-left corner
            drawLine(bracketColor, Offset(marginH, marginV), Offset(marginH + cornerLen, marginV), strokeW)
            drawLine(bracketColor, Offset(marginH, marginV), Offset(marginH, marginV + cornerLen), strokeW)

            // Top-right corner
            drawLine(bracketColor, Offset(marginH + rectW, marginV), Offset(marginH + rectW - cornerLen, marginV), strokeW)
            drawLine(bracketColor, Offset(marginH + rectW, marginV), Offset(marginH + rectW, marginV + cornerLen), strokeW)

            // Bottom-left corner
            drawLine(bracketColor, Offset(marginH, marginV + rectH), Offset(marginH + cornerLen, marginV + rectH), strokeW)
            drawLine(bracketColor, Offset(marginH, marginV + rectH), Offset(marginH, marginV + rectH - cornerLen), strokeW)

            // Bottom-right corner
            drawLine(bracketColor, Offset(marginH + rectW, marginV + rectH), Offset(marginH + rectW - cornerLen, marginV + rectH), strokeW)
            drawLine(bracketColor, Offset(marginH + rectW, marginV + rectH), Offset(marginH + rectW, marginV + rectH - cornerLen), strokeW)
        }

        // Top Control Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .testTag("camera_close_button")
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close Scanner", tint = Color.White)
            }

            // Document detection status pill
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF38BDF8))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Document Viewfinder",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }
            }

            // Flash Mode Toggle
            IconButton(
                onClick = {
                    flashMode = when (flashMode) {
                        ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                        ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                        else -> ImageCapture.FLASH_MODE_OFF
                    }
                    imageCapture?.flashMode = flashMode
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .testTag("camera_flash_toggle")
            ) {
                Icon(
                    imageVector = when (flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Filled.FlashOn
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Filled.FlashAuto
                        else -> Icons.Filled.FlashOff
                    },
                    contentDescription = "Flash mode",
                    tint = if (flashMode != ImageCapture.FLASH_MODE_OFF) Color(0xFFFBBF24) else Color.White
                )
            }
        }

        // Bottom Controls Section
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            // Page counter badge and "Finish" pill
            if (pageCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = "$pageCount ${if (pageCount == 1) "page captured" else "pages captured"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }

                    Button(
                        onClick = onFinish,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("camera_finish_button")
                    ) {
                        Text("Review (${pageCount})", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Shutter / Action Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery shortcut or last thumbnail
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable { onImportGallery() }
                        .testTag("camera_gallery_shortcut"),
                    contentAlignment = Alignment.Center
                ) {
                    if (latestThumbnailPath != null && File(latestThumbnailPath).exists()) {
                        AsyncImage(
                            model = File(latestThumbnailPath),
                            contentDescription = "Last captured page",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.PhotoLibrary,
                            contentDescription = "Import from gallery",
                            tint = Color.White
                        )
                    }
                }

                // Shutter Button
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(shutterScale)
                        .clip(CircleShape)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable {
                            isShutterPressed = true
                            val cacheDir = File(context.cacheDir, "scan_raw").apply { mkdirs() }
                            val photoFile = File(cacheDir, "capture_${System.currentTimeMillis()}.jpg")
                            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                            imageCapture?.takePicture(
                                outputOptions,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        isShutterPressed = false
                                        onImageCaptured(photoFile.absolutePath)
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        isShutterPressed = false
                                    }
                                }
                            )
                        }
                        .testTag("camera_shutter_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }

                // Camera Switch (Front/Back)
                IconButton(
                    onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    },
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                        .testTag("camera_lens_switch")
                ) {
                    Icon(
                        imageVector = Icons.Filled.FlipCameraAndroid,
                        contentDescription = "Switch camera",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
