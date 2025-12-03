package com.echox.app.data.api

import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.gson.*
import java.io.File
import kotlinx.coroutines.delay

/** X (Twitter) API v2 Service Handles OAuth, media upload, and thread creation */
class XApiService {

    private val client =
            HttpClient(CIO) {
                install(ContentNegotiation) { gson() }
                install(Logging) {
                    logger =
                            object : Logger {
                                override fun log(message: String) {
                                    Log.d("XApi_Ktor", message)
                                }
                            }
                    level = LogLevel.BODY
                }
            }

    /** Upload a video file to X Media API (V2) Returns media_id needed for tweet creation */
    suspend fun uploadMedia(
            videoFile: File,
            accessToken: String,
            onError: (String) -> Unit = {}
    ): String? {
        return try {
            val fileSize = videoFile.length()
            val fileSizeMB = fileSize / (1024.0 * 1024.0)
            
            // Log file size for debugging
            android.util.Log.d(
                    "EchoX_Upload",
                    "Uploading video: ${videoFile.name}, Size: ${String.format("%.2f", fileSizeMB)} MB"
            )
            
            // X API limit: 512MB per video
            val MAX_FILE_SIZE_BYTES = 512L * 1024 * 1024
            if (fileSize > MAX_FILE_SIZE_BYTES) {
                val errorMsg = "Video file too large: ${String.format("%.2f", fileSizeMB)} MB (max: 512 MB)"
                android.util.Log.e("EchoX_Upload", errorMsg)
                onError(errorMsg)
                return null
            }
            
            // 1. INIT
            val initResponse: HttpResponse =
                    client.post("https://api.twitter.com/2/media/upload/initialize") {
                        header("Authorization", "Bearer $accessToken")
                        contentType(ContentType.Application.Json)
                        setBody(mapOf("media_category" to "tweet_video", "total_bytes" to fileSize))
                    }

            val initBody = initResponse.bodyAsText()
            Log.d("XApi", "Upload INIT response: $initBody")

            if (initResponse.status != HttpStatusCode.Accepted &&
                            initResponse.status != HttpStatusCode.Created &&
                            initResponse.status != HttpStatusCode.OK
            ) {
                val errorBody = initResponse.bodyAsText()
                Log.e("XApi", "Init failed with status ${initResponse.status}: $errorBody")
                onError("Init failed: $errorBody")
                return null
            }

            // Extract media_id from V2 response
            // V2 Response format: {"data":{"id":"123","media_key":"7_123"}}
            val mediaId = extractJsonValue(initBody, "id")

            if (mediaId == null) {
                Log.e("XApi", "Init failed: No id in response. Response body: $initBody")
                onError("Init failed: No id in response")
                return null
            }
            
            Log.d("XApi", "Media upload initialized. Media ID: $mediaId")

            // 2. APPEND (Chunked Upload)
            // V2 requires small chunks (e.g. 1MB) to avoid 413 Payload Too Large
            val chunkSize = 1 * 1024 * 1024
            val buffer = ByteArray(chunkSize)
            var segmentIndex = 0

            videoFile.inputStream().use { input ->
                var bytesRead = input.read(buffer)
                while (bytesRead != -1) {
                    // Create a copy of the valid bytes if read < chunkSize
                    val chunk = if (bytesRead == chunkSize) buffer else buffer.copyOf(bytesRead)

                    val appendResponse =
                            client.submitFormWithBinaryData(
                                    url = "https://api.twitter.com/2/media/upload/$mediaId/append",
                                    formData =
                                            formData {
                                                append("segment_index", segmentIndex.toString())
                                                // NOTE: Do NOT send media_id in body for V2 APPEND,
                                                // it's in the URL
                                                append(
                                                        "media",
                                                        chunk,
                                                        Headers.build {
                                                            append(
                                                                    HttpHeaders.ContentType,
                                                                    "application/octet-stream"
                                                            )
                                                            append(
                                                                    HttpHeaders.ContentDisposition,
                                                                    "filename=video.mp4"
                                                            )
                                                        }
                                                )
                                            }
                            ) { header("Authorization", "Bearer $accessToken") }

                    if (appendResponse.status != HttpStatusCode.NoContent &&
                                    appendResponse.status != HttpStatusCode.OK &&
                                    appendResponse.status != HttpStatusCode.Created
                    ) {
                        val errorBody = appendResponse.bodyAsText()
                        Log.e("XApi", "Append failed for segment $segmentIndex with status ${appendResponse.status}: $errorBody")
                        onError("Append failed (seg $segmentIndex): $errorBody")
                        return null
                    }
                    
                    Log.d("XApi", "Appended segment $segmentIndex successfully")
                    segmentIndex++
                    bytesRead = input.read(buffer)
                }
            }

            // 3. FINALIZE
            val finalizeResponse: HttpResponse =
                    client.post("https://api.twitter.com/2/media/upload/$mediaId/finalize") {
                        header("Authorization", "Bearer $accessToken")
                        contentType(ContentType.Application.Json)
                        setBody("""{"media_id": "$mediaId"}""")
                    }

            val finalizeBody = finalizeResponse.bodyAsText()
            Log.d("XApi", "Media Finalize response: $finalizeBody")

            if (finalizeResponse.status != HttpStatusCode.Created &&
                            finalizeResponse.status != HttpStatusCode.OK
            ) {
                val errorBody = finalizeResponse.bodyAsText()
                Log.e("XApi", "Finalize failed with status ${finalizeResponse.status}: $errorBody")
                onError("Finalize failed: $errorBody")
                return null
            }
            
            // Parse processing_info from finalize response
            // Format: {"data":{"processing_info":{"check_after_secs":1,"state":"pending"},...}}
            val processingInfo = parseProcessingInfo(finalizeBody)
            if (processingInfo != null) {
                val state = processingInfo.first
                val checkAfterSecs = processingInfo.second
                
                Log.d("XApi", "Media processing state: $state, check after: ${checkAfterSecs}s")
                
                if (state == "pending" || state == "in_progress") {
                    // Wait the recommended time, then add extra buffer
                    val waitTime = (checkAfterSecs * 1000L).coerceAtLeast(2000L)
                    Log.d("XApi", "Waiting ${waitTime}ms for media processing...")
                    try {
                        kotlinx.coroutines.delay(waitTime)
                    } catch (e: Exception) {
                        Thread.sleep(waitTime)
                    }
                } else if (state == "succeeded") {
                    Log.d("XApi", "Media processing already succeeded")
                } else if (state == "failed") {
                    Log.e("XApi", "Media processing failed")
                    onError("Media processing failed")
                    return null
                }
            } else {
                // No processing_info means it's ready immediately (very small files)
                Log.d("XApi", "No processing_info found, media should be ready immediately")
            }

            Log.d("XApi", "Media upload completed successfully. Media ID: $mediaId")
            mediaId
        } catch (e: Exception) {
            Log.e("XApi", "Media upload failed: ${e.message}", e)
            onError("Upload failed: ${e.message}")
            null
        }
    }

    // Helper to extract value from JSON (simple regex)
    private fun extractJsonValue(json: String, key: String): String? {
        val regex = """"$key"\s*:\s*"([^"]+)"""".toRegex()
        return regex.find(json)?.groupValues?.get(1)
    }
    
    // Parse processing_info from finalize response: {"state":"pending","check_after_secs":1}
    // Returns Pair(state, check_after_secs) or null if not found
    private fun parseProcessingInfo(json: String): Pair<String, Int>? {
        // Look for processing_info block
        val processingInfoRegex = """"processing_info"\s*:\s*\{[^}]*"state"\s*:\s*"([^"]+)"[^}]*"check_after_secs"\s*:\s*(\d+)""".toRegex()
        val match = processingInfoRegex.find(json)
        if (match != null) {
            val state = match.groupValues[1]
            val checkAfterSecs = match.groupValues[2].toIntOrNull() ?: 1
            return Pair(state, checkAfterSecs)
        }
        return null
    }

    /** Create a tweet with optional media and reply-to Returns tweet ID if successful */
    suspend fun createTweet(
            text: String,
            mediaId: String?,
            replyToTweetId: String?,
            accessToken: String
    ): String? {
        return try {
            val requestBody = buildMap {
                put("text", text)
                mediaId?.let { put("media", mapOf("media_ids" to listOf(it))) }
                replyToTweetId?.let { put("reply", mapOf("in_reply_to_tweet_id" to it)) }
            }

            val response: HttpResponse =
                    client.post("https://api.twitter.com/2/tweets") {
                        header("Authorization", "Bearer $accessToken")
                        contentType(ContentType.Application.Json)
                        setBody(requestBody)
                    }

            val responseText = response.bodyAsText()
            Log.d("XApi", "Tweet creation response: $responseText")

            // Check for errors first
            if (response.status != HttpStatusCode.Created && response.status != HttpStatusCode.OK) {
                val errorMsg = if (responseText.contains("invalid") || responseText.contains("media")) {
                    "Media not ready: $responseText"
                } else {
                    "Tweet creation failed: $responseText"
                }
                Log.e("XApi", errorMsg)
                return null
            }

            // Extract tweet ID (simplified)
            val tweetId = extractTweetId(responseText)
            if (tweetId != null) {
                Log.d("XApi", "Tweet created successfully. Tweet ID: $tweetId")
            } else {
                Log.w("XApi", "Tweet creation response received but no tweet ID found: $responseText")
            }
            tweetId
        } catch (e: Exception) {
            Log.e("XApi", "Tweet creation failed: ${e.message}", e)
            null
        }
    }

    /** Post multiple videos as a threaded series of tweets */
    suspend fun postThread(
            videos: List<File>,
            baseText: String,
            accessToken: String,
            onProgress: (String) -> Unit
    ): Boolean {
        var previousTweetId: String? = null

        videos.forEachIndexed { index, video ->
            val partNumber = index + 1
            val totalParts = videos.size

            onProgress("Uploading video $partNumber/$totalParts...")
            val mediaId =
                    uploadMedia(video, accessToken) { errorMsg ->
                        onProgress("Upload Error: $errorMsg")
                    }
            if (mediaId.isNullOrBlank()) {
                Log.e("XApi", "Media upload returned null/empty for video $partNumber/$totalParts")
                onProgress("Upload failed for video $partNumber. See above.")
                return false
            }
            Log.d("XApi", "Video $partNumber/$totalParts uploaded successfully. Media ID: $mediaId")

            // CRITICAL: Wait for media processing to complete before creating tweet
            // X API requires media to be fully processed before attaching to tweets
            onProgress("Waiting for video $partNumber/$totalParts to process...")
            val isReady = waitForMediaProcessing(mediaId, accessToken, onProgress)
            if (!isReady) {
                Log.e("XApi", "Media $mediaId failed to process or timed out for video $partNumber/$totalParts")
                onProgress("Video $partNumber processing failed or timed out")
                return false
            }
            Log.d("XApi", "Media $mediaId is ready for tweet creation (video $partNumber/$totalParts)")

            onProgress("Posting tweet $partNumber/$totalParts...")
            val tweetText =
                    if (totalParts > 1) {
                        "$baseText ($partNumber/$totalParts)"
                    } else {
                        baseText
                    }

            // Try creating tweet with retry logic (media might still be processing)
            var tweetId: String? = null
            var retryCount = 0
            val maxRetries = 5
            
            while (tweetId.isNullOrBlank() && retryCount < maxRetries) {
                tweetId = createTweet(
                        text = tweetText,
                        mediaId = mediaId,
                        replyToTweetId = previousTweetId,
                        accessToken = accessToken
                )
                
                if (tweetId.isNullOrBlank()) {
                    retryCount++
                    if (retryCount < maxRetries) {
                        val waitTime = 2000L shl (retryCount - 1) // Exponential backoff: 2s, 4s, 8s, 16s...
                        Log.w("XApi", "Tweet creation failed for part $partNumber, exponential backoff: waiting ${waitTime}ms (attempt $retryCount/$maxRetries)")
                        onProgress("Media still processing, exponential backoff: waiting ${waitTime/1000}s...")
                        kotlinx.coroutines.delay(waitTime)
                    }
                }
            }

            if (tweetId.isNullOrBlank()) {
                Log.e("XApi", "Tweet creation failed after $maxRetries attempts for part $partNumber/$totalParts")
                onProgress("Failed to post tweet $partNumber after multiple retries")
                return false
            }
            Log.d("XApi", "Tweet $partNumber/$totalParts posted successfully. Tweet ID: $tweetId")

            previousTweetId = tweetId
            onProgress("Posted $partNumber/$totalParts successfully")
        }

        return true
    }

    /** Wait for media processing to complete 
     * NOTE: V1.1 status endpoint doesn't work with V2 media (returns 403)
     * We rely on processing_info from finalize response and retry logic in tweet creation
     */
    private suspend fun waitForMediaProcessing(
            mediaId: String,
            accessToken: String,
            onProgress: (String) -> Unit
    ): Boolean {
        // The actual waiting happens in uploadMedia() based on processing_info
        // This function is kept for compatibility but doesn't do status polling
        // since V1.1 endpoint returns 403 for V2 media
        Log.d("XApi", "Media processing wait handled by uploadMedia() processing_info parsing")
        return true // Assume ready, retry logic in tweet creation will handle if not
    }

    // Helper functions for parsing JSON responses
    private fun extractMediaId(response: String): String? {
        val regex = """"media_id_string":"(\d+)"""".toRegex()
        val result = regex.find(response)?.groupValues?.get(1)
        try {
            Log.d(
                    "XApi",
                    "extractMediaId: ${if (result != null) "found $result" else "NOT FOUND in: $response"}"
            )
        } catch (e: RuntimeException) {
            Log.w("XApi", "extractMediaId error: ${e.message}")
        }
        return result?.takeIf { it.isNotBlank() }
    }

    private fun extractTweetId(response: String): String? {
        val regex = """"id":"(\d+)"""".toRegex()
        val result = regex.find(response)?.groupValues?.get(1)
        try {
            Log.d(
                    "XApi",
                    "extractTweetId: ${if (result != null) "found $result" else "NOT FOUND in: $response"}"
            )
        } catch (e: RuntimeException) {
            Log.w("XApi", "extractTweetId error: ${e.message}")
        }
        return result?.takeIf { it.isNotBlank() }
    }

    /** Check media processing status (STATUS command) */
    suspend fun checkMediaStatus(mediaId: String, accessToken: String): Boolean {
        return try {
            val response: HttpResponse =
                    client.get("https://upload.twitter.com/1.1/media/upload.json") {
                        parameter("command", "STATUS")
                        parameter("media_id", mediaId)
                        header("Authorization", "Bearer $accessToken")
                    }

            val responseText = response.bodyAsText()
            Log.d("XApi", "Media Status response: $responseText")

            // Check HTTP status first
            if (response.status != HttpStatusCode.OK) {
                Log.w("XApi", "Status check returned ${response.status} for media ID: $mediaId")
                // If we get a non-OK status, assume it might be ready (could be 404 if already processed)
                // We'll let the tweet creation attempt determine if it's actually ready
                return true
            }

            // Parse state from response body
            if (responseText.contains(""""state":"succeeded"""")) return true
            if (responseText.contains(""""state":"failed"""")) return false
            
            // If processing_info is missing, assume it's ready (small files or already processed)
            if (!responseText.contains("processing_info")) {
                Log.d("XApi", "No processing_info found for media $mediaId, assuming ready")
                return true
            }

            // Still processing (pending/in_progress)
            return false
        } catch (e: Exception) {
            Log.e("XApi", "Check status failed for media ID: $mediaId", e)
            // On error, assume not ready to be safe
            false
        }
    }

    fun close() {
        client.close()
    }
}
