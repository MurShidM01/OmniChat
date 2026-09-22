package com.example.data.local

import androidx.room.*
import com.example.data.model.ProviderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderDao {
    @Query("SELECT * FROM providers ORDER BY sortOrder ASC, createdAt ASC")
    fun getAllProviders(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getAllProvidersList(): List<ProviderEntity>

    @Query("SELECT * FROM providers WHERE isEnabled = 1 ORDER BY sortOrder ASC, createdAt ASC")
    fun getEnabledProviders(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers WHERE id = :id LIMIT 1")
    suspend fun getProviderById(id: String): ProviderEntity?

    @Query("SELECT * FROM providers WHERE id = :id LIMIT 1")
    fun observeProviderById(id: String): Flow<ProviderEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(provider: ProviderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(providers: List<ProviderEntity>)

    @Delete
    suspend fun delete(provider: ProviderEntity)

    @Query("DELETE FROM providers WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE providers SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun setEnabled(id: String, isEnabled: Boolean)

    @Query("UPDATE providers SET sortOrder = :order WHERE id = :id")
    suspend fun updateSortOrder(id: String, order: Int)

    @Query("SELECT COUNT(*) FROM providers")
    suspend fun getProviderCount(): Int
}
