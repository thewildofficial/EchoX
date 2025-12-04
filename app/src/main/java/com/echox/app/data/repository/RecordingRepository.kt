package com.echox.app.data.repository

import android.content.Context
import com.echox.app.data.database.AppDatabase
import com.echox.app.data.database.Recording
import com.echox.app.data.database.RecordingDao
import kotlinx.coroutines.flow.Flow

class RecordingRepository(context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val dao: RecordingDao = database.recordingDao()
    
    fun getAllRecordings(): Flow<List<Recording>> = dao.getAllRecordings()
    
    suspend fun getRecordingById(id: Long): Recording? = dao.getRecordingById(id)
    
    suspend fun insertRecording(recording: Recording): Long = dao.insertRecording(recording)
    
    suspend fun deleteRecording(recording: Recording) = dao.deleteRecording(recording)
    
    suspend fun deleteRecordingById(id: Long) = dao.deleteRecordingById(id)
    
    suspend fun getRecordingCount(): Int = dao.getRecordingCount()
}
