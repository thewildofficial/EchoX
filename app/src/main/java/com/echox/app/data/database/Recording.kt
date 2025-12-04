package com.echox.app.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "recordings")
data class Recording(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val audioPath: String,
    val videoPath: String,
    val durationMs: Long,
    val amplitudesPath: String,
    val thumbnailPath: String? = null
) {
    fun getFormattedDate(): String {
        val date = Date(timestamp)
        val now = Date()
        val diff = now.time - timestamp
        
        return when {
            diff < 60_000 -> "Just now"
            diff < 3600_000 -> "${diff / 60_000}m ago"
            diff < 86400_000 -> "${diff / 3600_000}h ago"
            diff < 604800_000 -> "${diff / 86400_000}d ago"
            else -> java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).format(date)
        }
    }
    
    fun getFormattedDuration(): String {
        val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }
}
