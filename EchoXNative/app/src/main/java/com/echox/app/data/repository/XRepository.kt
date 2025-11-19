
package com.echox.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.echox.app.data.api.XService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class XRepository(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secret_shared_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _isAuthenticated = MutableStateFlow(getToken() != null)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated

    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
            getToken()?.let { token ->
                request.addHeader("Authorization", "Bearer $token")
            }
            chain.proceed(request.build())
        }
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.twitter.com/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: XService = retrofit.create(XService::class.java)

    fun saveToken(token: String) {
        sharedPreferences.edit().putString("access_token", token).apply()
        _isAuthenticated.value = true
    }

    fun getToken(): String? {
        return sharedPreferences.getString("access_token", null)
    }

    fun logout() {
        sharedPreferences.edit().remove("access_token").apply()
        _isAuthenticated.value = false
    }
}
