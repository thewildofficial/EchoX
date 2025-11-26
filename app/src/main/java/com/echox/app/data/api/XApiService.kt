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
                                        println("XApi: $message")
                                    } catch (e: RuntimeException) {
                                        println("XApi: $message")
                                    }
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
            // 1. INIT
            val initResponse: HttpResponse =
                    client.post("https://api.twitter.com/2/media/upload/initialize") {
                        header("Authorization", "Bearer $accessToken")
                        contentType(ContentType.Application.Json)
                        setBody(mapOf("media_category" to "tweet_video", "total_bytes" to fileSize))
                    }

            val initBody = initResponse.bodyAsText()
            try {
                println("XApi: Upload INIT: $initBody")
            } catch (e: RuntimeException) {
                println("XApi: Upload INIT: $initBody")
            }

            if (initResponse.status != HttpStatusCode.Accepted &&
                            initResponse.status != HttpStatusCode.Created &&
                            initResponse.status != HttpStatusCode.OK
            ) {
                val errorBody = initResponse.bodyAsText()
                println("XApi: Init failed: $errorBody")
                onError("Init failed: $errorBody")
                return null
            }

            // Extract media_id from V2 response
            // V2 Response format: {"data":{"id":"123","media_key":"7_123"}}
            val mediaId = extractJsonValue(initBody, "id")

            if (mediaId == null) {
                onError("Init failed: No id in response")
                return null
            }

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
                        println("XApi: Append failed for segment $segmentIndex: $errorBody")
                        onError("Append failed (seg $segmentIndex): $errorBody")
                        return null
                    }
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
            try {
                println("XApi: Media Finalize: $finalizeBody")
            } catch (e: RuntimeException) {
                println("XApi: Media Finalize: $finalizeBody")
            }

            if (finalizeResponse.status != HttpStatusCode.Created &&
                            finalizeResponse.status != HttpStatusCode.OK
            ) {
                val errorBody = finalizeResponse.bodyAsText()
                println("XApi: Finalize failed: $errorBody")
                onError("Finalize failed: $errorBody")
                return null
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
            println("XApi ERROR: Media upload failed: ${e.message}")
            e.printStackTrace()
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
                println("XApi: Tweet created: $responseText")
            } catch (e: RuntimeException) {
                println("XApi: Tweet created: $responseText")
            }

            // Extract tweet ID (simplified)
            extractTweetId(responseText)
        } catch (e: Exception) {
            println("XApi ERROR: Tweet creation failed: ${e.message}")
            e.printStackTrace()
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
                println("XApi ERROR: Media upload returned null/empty for video $partNumber")
                onProgress("Upload failed for video $partNumber. See above.")
                return false
            }
            println("XApi: Video $partNumber uploaded, mediaId: $mediaId")

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
                println("XApi ERROR: Tweet creation returned null/empty for part $partNumber")
                onProgress("Failed to post tweet $partNumber")
                return false
            }
            println("XApi: Tweet $partNumber posted, tweetId: $tweetId")

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
                println("XApi: Media Status: $responseText")
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
            println("XApi ERROR: Check status failed: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    fun close() {
        client.close()
    }
}
