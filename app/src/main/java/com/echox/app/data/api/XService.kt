
package com.echox.app.data.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface XService {
    @GET("2/users/me")
    suspend fun getUserProfile(
        @Query("user.fields") userFields: String = "profile_image_url,verified,verified_type"
    ): UserProfileResponse

    @POST("2/tweets")
    suspend fun postTweet(@Body body: TweetRequest): TweetResponse

    // Media Upload (v1.1)
    @Multipart
    @POST("1.1/media/upload.json")
    suspend fun initUpload(
        @Part("command") command: RequestBody,
        @Part("total_bytes") totalBytes: RequestBody,
        @Part("media_type") mediaType: RequestBody,
        @Part("media_category") mediaCategory: RequestBody
    ): MediaInitResponse

    @Multipart
    @POST("1.1/media/upload.json")
    suspend fun appendUpload(
        @Part("command") command: RequestBody,
        @Part("media_id") mediaId: RequestBody,
        @Part("segment_index") segmentIndex: RequestBody,
        @Part media: MultipartBody.Part
    ): Unit

    @Multipart
    @POST("1.1/media/upload.json")
    suspend fun finalizeUpload(
        @Part("command") command: RequestBody,
        @Part("media_id") mediaId: RequestBody
    ): MediaFinalizeResponse

    @Multipart
    @POST("1.1/media/upload.json")
    suspend fun checkUploadStatus(
        @Part("command") command: RequestBody,
        @Part("media_id") mediaId: RequestBody
    ): MediaFinalizeResponse
}

data class UserProfileResponse(val data: UserData)
data class UserData(
    val id: String,
    val name: String,
    val username: String,
    val profile_image_url: String,
    val verified: Boolean,
    val verified_type: String? = null
)

data class TweetRequest(
    val text: String,
    val media: MediaIds? = null,
    val reply: ReplyData? = null
)
data class ReplyData(val in_reply_to_tweet_id: String)
data class MediaIds(val media_ids: List<String>)
data class TweetResponse(val data: TweetData)
data class TweetData(val id: String, val text: String)

data class MediaInitResponse(val media_id_string: String)
data class MediaFinalizeResponse(val media_id_string: String, val processing_info: ProcessingInfo?)
data class ProcessingInfo(val state: String, val check_after_secs: Int?)
