package com.bytegrep.oplist_manager

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.bytegrep.oplist_manager.data.session.SessionManager
import dagger.hilt.android.HiltAndroidApp
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class OpenListApp : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var baseClient: OkHttpClient

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    /** Coil 图片加载：仅对当前服务器同源请求附加认证头，避免 Token 泄露给第三方域名 */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val client = baseClient.newBuilder()
            .addInterceptor(sameOriginAuthInterceptor())
            .build()
        return ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .build()
    }

    private fun sameOriginAuthInterceptor() = Interceptor { chain ->
        val request = chain.request()
        val session = sessionManager.session.value
        val server = session?.server?.baseUrl?.toHttpUrlOrNull()
        val sameOrigin = server != null &&
            server.host == request.url.host && server.port == request.url.port && server.scheme == request.url.scheme
        if (sameOrigin && session != null) {
            chain.proceed(request.newBuilder().header("Authorization", session.token).build())
        } else {
            chain.proceed(request)
        }
    }
}
