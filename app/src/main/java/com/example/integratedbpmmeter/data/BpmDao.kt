package com.example.integratedbpmmeter.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BpmDao {
    @Query("SELECT * FROM bpm_records ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BpmRecord>>

    @Query(
        """
        SELECT * FROM bpm_records
        WHERE title LIKE '%' || :query || '%'
            OR COALESCE(artist, '') LIKE '%' || :query || '%'
            OR COALESCE(album, '') LIKE '%' || :query || '%'
        ORDER BY createdAt DESC
        """
    )
    fun search(query: String): Flow<List<BpmRecord>>

    @Query(
        """
        SELECT * FROM bpm_records
        WHERE LOWER(title) LIKE '%' || LOWER(:titleToken) || '%'
            AND (
                :artistToken IS NULL
                OR :artistToken = ''
                OR LOWER(COALESCE(artist, '')) LIKE '%' || LOWER(:artistToken) || '%'
            )
        ORDER BY confidence DESC, createdAt DESC
        LIMIT 12
        """
    )
    suspend fun findLocalReferences(titleToken: String, artistToken: String?): List<BpmRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: BpmRecord): Long

    @Update
    suspend fun update(record: BpmRecord)

    @Delete
    suspend fun delete(record: BpmRecord)
}
