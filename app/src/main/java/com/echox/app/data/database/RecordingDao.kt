package com.echox.app.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY timestamp DESC")
    fun getAllRecordings(): Flow<List<Recording>>
    
    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getRecordingById(id: Long): Recording?
    
    @Insert
    suspend fun insertRecording(recording: Recording): Long
    
    @Delete
    suspend fun deleteRecording(recording: Recording)
    
    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteRecordingById(id: Long)
    
    @Query("SELECT COUNT(*) FROM recordings")
    suspend fun getRecordingCount(): Int
}
