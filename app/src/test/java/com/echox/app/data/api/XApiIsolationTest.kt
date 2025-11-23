package com.echox.app.data.api

import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test

class XApiIsolationTest {

    // TODO: REPLACE THIS WITH A VALID USER ACCESS TOKEN (OAuth 2.0)
    // Generate one via Postman or X Developer Portal
    private val ACCESS_TOKEN: String by lazy {
        val properties = java.util.Properties()
        val searchPaths =
                listOf(
                        File("local.properties"),
                        File("../local.properties"),
                        File("../../local.properties"),
                        File("/Users/aban/drive/Projects/EchoX/local.properties")
                )
        var found = false
        for (file in searchPaths) {
            if (file.exists()) {
                println("✅ Found local.properties at: ${file.absolutePath}")
                properties.load(file.inputStream())
                found = true
                break
            }
        }
        if (!found) {
            println("❌ Could not find local.properties. CWD: ${File(".").absolutePath}")
        }
        properties.getProperty("X_TEST_USER_TOKEN") ?: ""
    }

    @Test
    fun testThreadPosting() = runBlocking {
        if (ACCESS_TOKEN.isBlank()) {
            println("⚠️ SKIPPING TEST: Please set X_TEST_USER_TOKEN in local.properties")
            return@runBlocking
        }

        val service = XApiService()
        val video1 = File("src/test/resources/test_video_1.mp4")
        val video2 = File("src/test/resources/test_video_2.mp4")

        if (!video1.exists() || !video2.exists()) {
            println(
                    "❌ Test assets missing! Please ensure test_video_1.mp4 and test_video_2.mp4 exist in src/test/resources"
            )
            return@runBlocking
        }

        println("🚀 Starting X API Isolation Test")

        // --- PHASE A: Upload Video 1 ---
        println("\n📤 Uploading Video 1...")
        val mediaId1 = service.uploadMedia(video1, ACCESS_TOKEN)
        if (mediaId1 == null) {
            println("❌ Video 1 Upload Failed (INIT/APPEND/FINALIZE)")
            return@runBlocking
        }
        println("✅ Video 1 Uploaded. Media ID: $mediaId1")

        // Poll for status
        waitForProcessing(service, mediaId1)

        // --- PHASE B: Upload Video 2 ---
        println("\n📤 Uploading Video 2...")
        val mediaId2 = service.uploadMedia(video2, ACCESS_TOKEN)
        if (mediaId2 == null) {
            println("❌ Video 2 Upload Failed")
            return@runBlocking
        }
        println("✅ Video 2 Uploaded. Media ID: $mediaId2")

        // Poll for status
        waitForProcessing(service, mediaId2)

        // --- PHASE C: Create Thread ---
        println("\n📝 Creating Root Tweet...")
        val tweetId1 =
                service.createTweet(
                        text = "EchoX Isolation Test - Chunk 1 ${System.currentTimeMillis()}",
                        mediaId = mediaId1,
                        replyToTweetId = null,
                        accessToken = ACCESS_TOKEN
                )
        if (tweetId1 == null) {
            println("❌ Root Tweet Failed")
            return@runBlocking
        }
        println("✅ Root Tweet Created. ID: $tweetId1")

        println("\n📝 Creating Reply Tweet...")
        val tweetId2 =
                service.createTweet(
                        text = "EchoX Isolation Test - Chunk 2 (Reply)",
                        mediaId = mediaId2,
                        replyToTweetId = tweetId1,
                        accessToken = ACCESS_TOKEN
                )
        if (tweetId2 == null) {
            println("❌ Reply Tweet Failed")
            return@runBlocking
        }
        println("✅ Reply Tweet Created. ID: $tweetId2")

        println("\n🎉 TEST PASSED! Check your X profile.")
        service.close()
    }

    private suspend fun waitForProcessing(service: XApiService, mediaId: String) {
        println("⏳ Polling processing status for $mediaId...")
        var attempts = 0
        while (attempts < 20) {
            val isReady = service.checkMediaStatus(mediaId, ACCESS_TOKEN)
            if (isReady) {
                println("✅ Media $mediaId is ready!")
                return
            }
            println("   ...still processing (Attempt ${attempts + 1})")
            delay(2000)
            attempts++
        }
        println("⚠️ Warning: Polling timed out or failed. Proceeding anyway (might fail)...")
    }
}
