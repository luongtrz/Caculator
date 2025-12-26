package com.example.caculateapp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for rice weighing sessions
 * All operations are suspend functions for use with Coroutines
 */
@Dao
interface RiceDao {
    
    /**
     * Insert a new rice session record
     * @return the ID of the inserted record
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: RiceRecord): Long
    
    /**
     * Get all rice session records ordered by timestamp (newest first)
     */
    @Query("SELECT * FROM rice_records ORDER BY createdAt DESC")
    fun getAllRecords(): Flow<List<RiceRecord>>
    
    /**
     * Get all records as a list (for one-time queries)
     */
    @Query("SELECT * FROM rice_records")
    suspend fun getAllRecordsList(): List<RiceRecord>
    
    /**
     * Get all records sorted by creation date (newest first)
     */
    @Query("SELECT * FROM rice_records ORDER BY createdAt DESC")
    fun getAllRecordsSortedByDate(): kotlinx.coroutines.flow.Flow<List<RiceRecord>>
    
    /**
     * Search records by customer name
     */
    @Query("SELECT * FROM rice_records WHERE customerName LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchRecords(query: String): kotlinx.coroutines.flow.Flow<List<RiceRecord>>
    
    /**
     * Get a specific record by ID
     */
    @Query("SELECT * FROM rice_records WHERE id = :recordId")
    suspend fun getRecordById(recordId: Long): RiceRecord?
    
    /**
     * Delete a specific record
     */
    @Delete
    suspend fun delete(record: RiceRecord)
    
    /**
     * Delete all records
     */
    @Query("DELETE FROM rice_records")
    suspend fun deleteAll()
}
