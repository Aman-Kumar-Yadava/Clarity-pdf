package com.example.data.model

enum class EnhancementMode(val displayName: String, val description: String) {
    ORIGINAL("Original", "Unmodified raw capture"),
    AUTO_ENHANCE("Auto", "Balanced contrast & tone"),
    DOCUMENT("Document", "High contrast black text on white"),
    BLACK_WHITE("B&W", "Sharp monochrome threshold"),
    GRAYSCALE("Grayscale", "Smooth balanced monochrome"),
    COLOR("Color", "Vibrant colors & corrected lighting"),
    PHOTO("Photo", "Preserves natural photographic gradations")
}

enum class PdfPageSize(val displayName: String, val widthPt: Int, val heightPt: Int) {
    A4("A4", 595, 842),
    LETTER("Letter", 612, 792),
    LEGAL("Legal", 612, 1008),
    AUTO("Fit to Image", 0, 0)
}

enum class PdfOrientation(val displayName: String) {
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape"),
    AUTO("Auto Detect")
}

enum class CompressionQuality(val displayName: String, val maxDimension: Int, val jpegQuality: Int) {
    STANDARD("Standard (Smallest · ~150KB/page)", 1280, 65),
    HIGH("High (Recommended · ~300KB/page)", 1600, 80),
    MAXIMUM("Maximum (Print Quality · ~500KB/page)", 2200, 90)
}

data class ScanPage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val originalImagePath: String,
    val processedImagePath: String? = null,
    val rotationDegrees: Int = 0,
    val enhancementMode: EnhancementMode = EnhancementMode.ORIGINAL,
    val brightness: Float = 0f, // -100f to +100f
    val contrast: Float = 1f,   // 0.5f to 2.0f
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f
)
