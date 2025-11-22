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
                    level = LogLevel.INFO
                }
            }

    /** Upload a video file to X Media API Returns media_id needed for tweet creation */
    suspend fun uploadMedia(videoFile: File, accessToken: String): String? {
        return try {
            // X Media Upload is chunked - INIT, APPEND, FINALIZE
            val response: HttpResponse =
                    client.submitFormWithBinaryData(
                            url = "https://upload.twitter.com/1.1/media/upload.json",
                            formData =
                                    formData {
                                        // INIT phase
                                        append("command", "INIT")
                                        append("total_bytes", videoFile.length().toString())
                                        append("media_type", "video/mp4")
                                        append("media_category", "tweet_video")
                                    }
                    ) { header("Authorization", "Bearer $accessToken") }

            val initResponse = response.bodyAsText()
            try {
                Log.d("XApi", "Upload INIT: $initResponse")
            } catch (e: RuntimeException) {
                println("XApi: Upload INIT: $initResponse")
            }

            // Extract media_id from response (simplified - need proper JSON parsing)
            val mediaId = extractMediaId(initResponse) ?: return null

            // APPEND phase (upload actual file in chunks)
            val chunkSize = 5 * 1024 * 1024 // 5MB chunks
            val bytes = videoFile.readBytes()
            var segmentIndex = 0

            bytes.toList().chunked(chunkSize).forEach { chunk ->
                client.submitFormWithBinaryData(
                        url = "https://upload.twitter.com/1.1/media/upload.json",
                        formData =
                                formData {
                                    append("command", "APPEND")
                                    append("media_id", mediaId)
                                    append("segment_index", segmentIndex.toString())
                                    append(
                                            "media",
                                            chunk.toByteArray(),
                                            Headers.build {
                                                append(HttpHeaders.ContentType, "video/mp4")
                                                append(
                                                        HttpHeaders.ContentDisposition,
                                                        "filename=video.mp4"
                                                )
                                            }
                                    )
                                }
                ) { header("Authorization", "Bearer $accessToken") }
                segmentIndex++
            }

            // FINALIZE phase
            client.submitFormWithBinaryData(
                    url = "https://upload.twitter.com/1.1/media/upload.json",
                    formData =
                            formData {
                                append("command", "FINALIZE")
                                append("media_id", mediaId)
                            }
            ) { header("Authorization", "Bearer $accessToken") }

            try {
                Log.d("XApi", "Media uploaded: $mediaId")
            } catch (e: RuntimeException) {
                println("XApi: Media uploaded: $mediaId")
            }
            mediaId
        } catch (e: Exception) {
            Log.e("XApi", "Media upload failed", e)
            null
        }
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

    fun close() {
        client.close()
    }
}
