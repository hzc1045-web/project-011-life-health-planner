package com.project011.lifehealthplanner.data.remote

import com.project011.lifehealthplanner.BuildConfig
import com.project011.lifehealthplanner.security.CompanionCredentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.Duration
import java.time.Instant
import java.util.UUID

object CompanionClient {
    fun unauthenticated(serverUrl: String): CompanionApi = build(serverUrl, null)

    fun authenticated(credentials: CompanionCredentials): CompanionApi =
        build(credentials.serverUrl, credentials)

    private fun build(serverUrl: String, credentials: CompanionCredentials?): CompanionApi {
        require(serverUrl.startsWith("https://") || (BuildConfig.DEBUG && serverUrl.startsWith("http://10.0.2.2"))) {
            "电脑中转地址必须使用 HTTPS"
        }
        val builder = OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(15))
            .readTimeout(Duration.ofSeconds(210))
            .writeTimeout(Duration.ofSeconds(30))
        if (credentials != null) {
            builder.addInterceptor(Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Authorization", "Bearer ${credentials.token}")
                    .header("X-Device-ID", credentials.deviceId)
                    .header("X-Request-Timestamp", Instant.now().epochSecond.toString())
                    .header("X-Request-Nonce", UUID.randomUUID().toString())
                    .build()
                chain.proceed(request)
            })
        }
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
                redactHeader("Authorization")
            })
        }
        return Retrofit.Builder()
            .baseUrl(serverUrl.trimEnd('/') + "/")
            .client(builder.build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CompanionApi::class.java)
    }
}
