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

/** X (Twitter) API v2 Service Handles OAuth, media upload, and thread creation */
class XApiService {

    private val client =
            HttpClient(CIO) {
                install(ContentNegotiation) { gson() }
                install(Logging) {
                    logger =
                            object : Logger {
                                override fun log(message: String) {
                                    try {
                                        Log.d("XApi", message)
                                    } catch (e: RuntimeException) {
                                        println("XApi: $message")
                                    }
                                }
                            }
                    level = LogLevel.BODY
                }
            }

    /** Upload a video file to X Media API (V2) Returns media_id needed for tweet creation */
    suspend fun uploadMedia(videoFile: File, accessToken: String): String? {
        return try {
            val fileSize = videoFile.length()
            // 1. INIT
            val initResponse: HttpResponse =
                    client.post("https://api.twitter.com/2/media/upload/initialize") {
                        header("Authorization", "Bearer $accessToken")
                        contentType(ContentType.Application.Json)
                        setBody(mapOf("media_category" to "tweet_video", "total_bytes" to fileSize))
                    }

            val initBody = initResponse.bodyAsText()
            try {
                Log.d("XApi", "Upload INIT: $initBody")
            } catch (e: RuntimeException) {
                println("XApi: Upload INIT: $initBody")
            }

            // Extract media_id and media_key from JSON response
            // Response format: {"data":{"id":"...","media_key":"..."}}
            val mediaId = extractJsonValue(initBody, "id") ?: return null

            // 2. APPEND (Chunked Upload)
            // V2 requires small chunks (e.g. 1MB) to avoid 413 Payload Too Large
            val chunkSize = 1 * 1024 * 1024
            val bytes = videoFile.readBytes()
            var segmentIndex = 0

            bytes.toList().chunked(chunkSize).forEach { chunk ->
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
                                                    chunk.toByteArray(),
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
                    println("XApi: Append failed for segment $segmentIndex: $errorBody")
                    return null
                }
                segmentIndex++
            }

            // 3. FINALIZE
            val finalizeResponse: HttpResponse =
                    client.post("https://api.twitter.com/2/media/upload/$mediaId/finalize") {
                        header("Authorization", "Bearer $accessToken")
                        contentType(ContentType.Application.Json)
                        setBody(mapOf("media_id" to mediaId))
                    }

            val finalizeBody = finalizeResponse.bodyAsText()
            try {
                Log.d("XApi", "Media Finalize: $finalizeBody")
            } catch (e: RuntimeException) {
                println("XApi: Media Finalize: $finalizeBody")
            }

            // Check for processing info and wait if needed
            if (finalizeBody.contains("processing_info")) {
                // Simple wait for small videos (robust polling would be better but complex without
                // JSON parsing)
                // The Python test showed 1s wait was enough for small files.
                // We'll wait 2 seconds to be safe.
                try {
                    kotlinx.coroutines.delay(2000)
                } catch (e: Exception) {
                    Thread.sleep(2000)
                }
            }

            mediaId
        } catch (e: Exception) {
            Log.e("XApi", "Media upload failed", e)
            null
        }
    }

    // Helper to extract value from JSON (simple regex)
    private fun extractJsonValue(json: String, key: String): String? {
        val regex = """"$key"\s*:\s*"([^"]+)"""".toRegex()
        return regex.find(json)?.groupValues?.get(1)
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
            try {
                Log.d("XApi", "Tweet created: $responseText")
            } catch (e: RuntimeException) {
                println("XApi: Tweet created: $responseText")
            }

            // Extract tweet ID (simplified)
            extractTweetId(responseText)
        } catch (e: Exception) {
            Log.e("XApi", "Tweet creation failed", e)
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
            val mediaId = uploadMedia(video, accessToken)
            if (mediaId.isNullOrBlank()) {
                Log.e("XApi", "Media upload returned null/empty for video $partNumber")
                onProgress("Upload failed for video $partNumber. Check logs.")
                return false
            }
            Log.d("XApi", "Video $partNumber uploaded, mediaId: $mediaId")

            onProgress("Posting tweet $partNumber/$totalParts...")
            val tweetText =
                    if (totalParts > 1) {
                        "$baseText ($partNumber/$totalParts)"
                    } else {
                        baseText
                    }

            val tweetId =
                    createTweet(
                            text = tweetText,
                            mediaId = mediaId,
                            replyToTweetId = previousTweetId,
                            accessToken = accessToken
                    )

            if (tweetId.isNullOrBlank()) {
                Log.e("XApi", "Tweet creation returned null/empty for part $partNumber")
                onProgress("Failed to post tweet $partNumber")
                return false
            }
            Log.d("XApi", "Tweet $partNumber posted, tweetId: $tweetId")

            previousTweetId = tweetId
            onProgress("Posted $partNumber/$totalParts successfully")
        }

        return true
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
            println(
                    "XApi: extractMediaId: ${if (result != null) "found $result" else "NOT FOUND in: $response"}"
            )
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
            println(
                    "XApi: extractTweetId: ${if (result != null) "found $result" else "NOT FOUND in: $response"}"
            )
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
            try {
                Log.d("XApi", "Media Status: $responseText")
            } catch (e: RuntimeException) {
                println("XApi: Media Status: $responseText")
            }

            // Parse state
            if (responseText.contains(""""state":"succeeded"""")) return true
            if (responseText.contains(""""state":"failed"""")) return false
            // If processing_info is missing, it might be already done or small file
            if (!responseText.contains("processing_info")) return true

            return false // Still processing (pending/in_progress)
        } catch (e: Exception) {
            Log.e("XApi", "Check status failed", e)
            false
        }
    }

    fun close() {
        client.close()
    }
}
