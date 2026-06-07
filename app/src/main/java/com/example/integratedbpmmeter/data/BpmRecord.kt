package com.example.integratedbpmmeter.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class BpmSourceType {
    TAP,
    NOW_PLAYING,
    FILE_ANALYSIS,
    PUBLIC_REFERENCE,
    PLAYBACK_CAPTURE,
    MIC_CAPTURE
}

@Entity(
    tableName = "bpm_records",
    indices = [
        Index("createdAt"),
        Index("title"),
        Index("artist")
    ]
)
data class BpmRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val bpm: Double,
    val sourceType: BpmSourceType,
    val sourceAppPackage: String? = null,
    val fileUri: String? = null,
    val confidence: Double,
    val categoryOverride: BpmSmartCategory? = null,
    val manuallyVerified: Boolean = false,
    val createdAt: Long
)

class BpmConverters {
    @TypeConverter
    fun sourceTypeToString(sourceType: BpmSourceType): String = sourceType.name

    @TypeConverter
    fun stringToSourceType(value: String): BpmSourceType {
        return runCatching { BpmSourceType.valueOf(value) }.getOrDefault(BpmSourceType.TAP)
    }

    @TypeConverter
    fun smartCategoryToString(value: BpmSmartCategory?): String? = value?.name

    @TypeConverter
    fun stringToSmartCategory(value: String?): BpmSmartCategory? {
        return value?.let { runCatching { BpmSmartCategory.valueOf(it) }.getOrNull() }
    }
}
