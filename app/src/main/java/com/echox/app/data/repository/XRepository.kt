package com.echox.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.echox.app.data.api.UserData
import com.echox.app.data.api.XService
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.openid.appauth.TokenResponse
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class XRepository(context: Context) {
    private val masterKey =
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()

    private val sharedPreferences: SharedPreferences =
            EncryptedSharedPreferences.create(
                    context,
                    "secret_shared_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

    private val gson = Gson()
    private val _isAuthenticated = MutableStateFlow(hasValidToken())
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _userProfile = MutableStateFlow(loadCachedProfile())
    val userProfile: StateFlow<UserData?> = _userProfile.asStateFlow()

    private val client =
            OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                        getAccessToken()?.let { token ->
                            request.addHeader("Authorization", "Bearer $token")
                        }
                        chain.proceed(request.build())
                    }
                    .build()

    private val retrofit =
            Retrofit.Builder()
                    .baseUrl("https://api.twitter.com/")
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()

    val api: XService = retrofit.create(XService::class.java)

    fun saveTokenResponse(tokenResponse: TokenResponse) {
        val accessToken = tokenResponse.accessToken
        if (!accessToken.isNullOrBlank()) {
            sharedPreferences.edit().putString(KEY_ACCESS_TOKEN, accessToken).apply()
        }

        tokenResponse.refreshToken?.let {
            sharedPreferences.edit().putString(KEY_REFRESH_TOKEN, it).apply()
        }

        tokenResponse.accessTokenExpirationTime?.let {
            sharedPreferences.edit().putLong(KEY_EXPIRES_AT, it).apply()
        }

        _isAuthenticated.value = hasValidToken()
    }

    fun getAccessToken(): String? =
            sharedPreferences.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun getRefreshToken(): String? =
            sharedPreferences.getString(KEY_REFRESH_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun isTokenExpired(): Boolean {
        val expiresAt = sharedPreferences.getLong(KEY_EXPIRES_AT, 0L)
        return expiresAt != 0L && System.currentTimeMillis() >= expiresAt
    }

    fun logout() {
        sharedPreferences
                .edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_EXPIRES_AT)
                .apply()
        _isAuthenticated.value = false
        _userProfile.value = null
        sharedPreferences.edit().remove(KEY_PROFILE_JSON).apply()
    }

    suspend fun refreshUserProfile(context: android.content.Context? = null) {
        try {
            context?.let {
                android.widget.Toast.makeText(
                                it,
                                "[FETCH] Getting profile...",
                                android.widget.Toast.LENGTH_SHORT
                        )
                        .show()
            }
            val response = api.getUserProfile()
            cacheUserProfile(response.data)
            context?.let {
                android.widget.Toast.makeText(
                                it,
                                "[FETCH] Profile loaded: ${response.data?.name}",
                                android.widget.Toast.LENGTH_LONG
                        )
                        .show()
            }
        } catch (error: Exception) {
            context?.let {
                android.widget.Toast.makeText(
                                it,
                                "[FETCH ERROR] ${error.message}",
                                android.widget.Toast.LENGTH_LONG
                        )
                        .show()
            }
            error.printStackTrace()
        }
    }

    private fun cacheUserProfile(user: UserData?) {
        if (user == null) return
        _userProfile.value = user
        runCatching {
            sharedPreferences.edit().putString(KEY_PROFILE_JSON, gson.toJson(user)).apply()
        }
    }

    private fun loadCachedProfile(): UserData? {
        val json = sharedPreferences.getString(KEY_PROFILE_JSON, null) ?: return null
        return runCatching { gson.fromJson(json, UserData::class.java) }.getOrNull()
    }
    private fun hasValidToken(): Boolean {
        val token = sharedPreferences.getString(KEY_ACCESS_TOKEN, null)
        if (token.isNullOrBlank()) return false
        return !isTokenExpired()
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_PROFILE_JSON = "profile_json"
    }
}
