package com.example.integratedbpmmeter.data

data class BpmLibraryStats(
    val totalRecords: Int = 0,
    val reviewCount: Int = 0,
    val manuallyVerifiedCount: Int = 0,
    val localFileCount: Int = 0,
    val samsungMusicCount: Int = 0,
    val samsungMissingFileCount: Int = 0,
    val youtubeMusicCount: Int = 0
)

fun List<BpmRecord>.toBpmLibraryStats(): BpmLibraryStats {
    val linkedFileUris = mapNotNull { it.fileUri?.takeIf { uri -> uri.isNotBlank() } }
        .distinct()
    return BpmLibraryStats(
        totalRecords = size,
        reviewCount = count { it.needsBpmReview() },
        manuallyVerifiedCount = count { it.manuallyVerified },
        localFileCount = linkedFileUris.size,
        samsungMusicCount = count { it.isSamsungMusicSource() },
        samsungMissingFileCount = count { it.isSamsungMusicSource() && it.fileUri.isNullOrBlank() },
        youtubeMusicCount = count { it.isYouTubeMusicSource() }
    )
}

fun BpmRecord.isSamsungMusicSource(): Boolean {
    return sourceAppPackage == SAMSUNG_MUSIC_PACKAGE_LEGACY ||
        sourceAppPackage == SAMSUNG_MUSIC_PACKAGE
}

fun BpmRecord.isYouTubeMusicSource(): Boolean {
    return sourceAppPackage == YOUTUBE_MUSIC_PACKAGE
}

private const val SAMSUNG_MUSIC_PACKAGE_LEGACY = "com.sec.android.app.music"
private const val SAMSUNG_MUSIC_PACKAGE = "com.samsung.android.app.music"
private const val YOUTUBE_MUSIC_PACKAGE = "com.google.android.apps.youtube.music"
