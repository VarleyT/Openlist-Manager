package com.bytegrep.oplist_manager.di

import com.bytegrep.oplist_manager.BuildConfig
import com.bytegrep.oplist_manager.core.security.KeystoreCipher
import com.bytegrep.oplist_manager.core.security.SecretCipher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val CONNECT_TIMEOUT_SEC = 15L
    private const val IO_TIMEOUT_SEC = 60L

    /** Debug 日志中需脱敏的 URL 参数（大小写敏感，常见写法均列出） */
    private val SENSITIVE_QUERY_PARAMS = arrayOf(
        "sign", "token", "access_token", "auth_key", "signature", "Signature",
        "X-Amz-Signature", "X-Amz-Credential", "X-Amz-Security-Token", "OSSAccessKeyId",
    )

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(IO_TIMEOUT_SEC, TimeUnit.SECONDS)
        .writeTimeout(IO_TIMEOUT_SEC, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                // 仅调试版记录请求行，认证头脱敏
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                        redactHeader("Authorization")
                        // OpenList 签名链接与第三方存储预签名直链的敏感参数
                        redactQueryParams(*SENSITIVE_QUERY_PARAMS)
                    },
                )
            }
        }
        .build()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {
    @Binds
    abstract fun bindSecretCipher(impl: KeystoreCipher): SecretCipher
}
