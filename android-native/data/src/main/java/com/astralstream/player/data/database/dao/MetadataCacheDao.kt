package com.astralstream.player.data.database.dao

import androidx.room.*
import com.astralstream.player.data.database.entities.MetadataCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MetadataCacheDao {
    @Query("SELECT * FROM metadata_cache WHERE videoHash = :videoHash")
    suspend fun getMetadataByVideoHash(videoHash: String): MetadataCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: MetadataCacheEntity)

    @Update
    suspend fun updateMetadata(metadata: MetadataCacheEntity)

    @Delete
    suspend fun deleteMetadata(metadata: MetadataCacheEntity)

    @Query("DELETE FROM metadata_cache WHERE videoHash = :videoHash")
    suspend fun deleteMetadataByVideoHash(videoHash: String)

    @Query("DELETE FROM metadata_cache WHERE cachedAt < :beforeTime")
    suspend fun deleteOldMetadata(beforeTime: Long)

    @Query("SELECT * FROM metadata_cache ORDER BY cachedAt DESC")
    fun getAllMetadata(): Flow<List<MetadataCacheEntity>>
}