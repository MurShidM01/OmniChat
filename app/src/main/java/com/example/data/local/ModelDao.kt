package com.example.data.local

import androidx.room.*
import com.example.data.model.ModelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {
    @Query("SELECT * FROM models ORDER BY isFavorite DESC, lastUsedAt DESC, displayName ASC")
    fun getAllModels(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models ORDER BY isFavorite DESC, lastUsedAt DESC, displayName ASC")
    suspend fun getAllModelsList(): List<ModelEntity>

    @Query("SELECT * FROM models WHERE providerId = :providerId ORDER BY isFavorite DESC, lastUsedAt DESC, displayName ASC")
    fun getModelsByProvider(providerId: String): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE id = :id LIMIT 1")
    suspend fun getModelById(id: String): ModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(model: ModelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(models: List<ModelEntity>)

    @Query("UPDATE models SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    @Query("UPDATE models SET lastUsedAt = :timestamp WHERE id = :id")
    suspend fun updateLastUsed(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM models WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM models WHERE providerId = :providerId AND isCustom = 0")
    suspend fun deleteDiscoveredByProvider(providerId: String)
}
