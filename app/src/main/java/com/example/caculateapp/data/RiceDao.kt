package com.example.caculateapp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for local SQLite Room database operations
 */
@Dao
interface RiceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: RiceRecord): Long

    @Update
    suspend fun update(record: RiceRecord)

    @Delete
    suspend fun delete(record: RiceRecord)

    @Query("DELETE FROM rice_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM rice_records ORDER BY createdAt DESC")
    fun getAllRecords(): Flow<List<RiceRecord>>

    @Query("SELECT * FROM rice_records WHERE id = :id")
    suspend fun getRecordById(id: Long): RiceRecord?

    @Query("SELECT * FROM rice_records WHERE customerName LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchRecords(query: String): Flow<List<RiceRecord>>
}
