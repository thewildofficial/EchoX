package com.echox.app.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.echox.app.BuildConfig
import com.echox.app.data.repository.XRepository
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues

class AuthManager(private val context: Context) {

    private val authService = AuthorizationService(context)
    private val clientId = BuildConfig.X_CLIENT_ID.orEmpty()
    private val redirectUri = Uri.parse("echox://auth")
    private val mainHandler = Handler(Looper.getMainLooper())

    private val serviceConfig =
            AuthorizationServiceConfiguration(
                    Uri.parse("https://twitter.com/i/oauth2/authorize"),
                    Uri.parse("https://api.twitter.com/2/oauth2/token")
            )

    val isConfigured: Boolean
        get() = clientId.isNotBlank()

    val configurationError: String?
        get() =
                if (isConfigured) null
                else "Missing X OAuth client id. Set X_CLIENT_ID in local.properties."

    fun getAuthIntent(): Intent? {
        if (!isConfigured) return null

        val authRequest =
                AuthorizationRequest.Builder(
                                serviceConfig,
                                clientId,
                                ResponseTypeValues.CODE,
                                redirectUri
                        )
                        .setScopes("tweet.read", "tweet.write", "users.read", "offline.access")
                        .build()

        return authService.getAuthorizationRequestIntent(authRequest)
    }

    fun handleAuthResponse(
        intent: Intent?,
        repository: XRepository,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (intent == null) {
            mainHandler.post { onError("Authentication canceled") }
            return
        }

        val authorizationResponse = AuthorizationResponse.fromIntent(intent)
        val authorizationError = AuthorizationException.fromIntent(intent)

        when {
            authorizationResponse != null -> {
                val tokenRequest = authorizationResponse.createTokenExchangeRequest()
                authService.performTokenRequest(tokenRequest) { tokenResponse, tokenException ->
                    if (tokenResponse != null && !tokenResponse.accessToken.isNullOrBlank()) {
                        repository.saveTokenResponse(tokenResponse)
                        mainHandler.post { onSuccess() }
                    } else {
                        val message =
                            tokenException?.errorDescription ?: "Unable to complete authentication."
                        mainHandler.post { onError(message) }
                    }
                }
            }
            authorizationError != null -> {
                mainHandler.post {
                    onError(authorizationError.errorDescription ?: "Authentication canceled.")
                }
            }
            else -> mainHandler.post { onError("Unable to complete authentication.") }
        }
    }

    fun dispose() {
        authService.dispose()
    }
}
