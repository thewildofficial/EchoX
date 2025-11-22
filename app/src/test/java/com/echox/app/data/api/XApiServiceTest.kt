package com.echox.app.data.api

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
import java.io.FileInputStream
import java.util.Properties
import kotlinx.coroutines.runBlocking
import org.junit.Test

class XApiServiceTest {

    @Test
    fun testMediaUploadAndTweet() {
        runBlocking {
            // 1. Load credentials from local.properties
            val properties = Properties()
            // Adjust path to local.properties depending on where test is run from
            // Usually unit tests run from app/ directory context, so ../local.properties is correct
            val localPropFile = File("../local.properties")
            if (localPropFile.exists()) {
                properties.load(FileInputStream(localPropFile))
            } else {
                println("local.properties not found at ${localPropFile.absolutePath}")
                return@runBlocking
            }

            // Try to get user token if available, otherwise fall back to Bearer (which might fail
            // for posting)
            // But for this test, we just want to see the API response.
            val accessToken = properties.getProperty("X_BEARER_TOKEN")
            println("Using Access Token: ${accessToken?.take(10)}...")

            val service = XApiService()

            // 2. Create a dummy video file
            val dummyVideo = File("test_video.mp4")
            if (!dummyVideo.exists()) {
                dummyVideo.writeBytes(ByteArray(1024 * 100)) // 100KB dummy file
            }

            println("Starting upload test...")

            // 3. Test Upload
            val mediaId = service.uploadMedia(dummyVideo, accessToken ?: "")
            println("Upload Result: $mediaId")

            if (mediaId != null) {
                println("Upload SUCCESS! Media ID: $mediaId")

                // 4. Test Tweet
                val tweetId =
                        service.createTweet(
                                text = "Automated test tweet from EchoX unit test",
                                mediaId = mediaId,
                                replyToTweetId = null,
                                accessToken = accessToken ?: ""
                        )
                println("Tweet Result: $tweetId")
            } else {
                println("Upload FAILED")
            }

            if (dummyVideo.exists()) {
                dummyVideo.delete()
            }

            service.close()
        }
    }
}
