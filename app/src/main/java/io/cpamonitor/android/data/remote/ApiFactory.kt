package io.cpamonitor.android.data.remote

import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Singleton
class ApiFactory @Inject constructor(private val json: Json) {
    private var activeSession: ActiveSession? = null

    @Synchronized
    fun create(baseUrl: String, adminKey: String): CpampApi {
        val normalizedBaseUrl = baseUrl.trimEnd('/')
        val keyDigest = MessageDigest.getInstance("SHA-256").digest(adminKey.toByteArray(Charsets.UTF_8))
        activeSession?.let { active ->
            if (active.baseUrl == normalizedBaseUrl && MessageDigest.isEqual(active.keyDigest, keyDigest)) {
                return active.api
            }
            active.close()
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor(ReadOnlyPathInterceptor())
            .addInterceptor { chain ->
                val original = chain.request()
                val builder = original.newBuilder().header("Accept", "application/json")
                if (original.url.encodedPath.trimEnd('/') != PUBLIC_INFO_PATH) {
                    builder.header("Authorization", "Bearer $adminKey")
                }
                val request = builder.build()
                chain.proceed(request)
            }
            // An application interceptor observes OkHttp's transparently decompressed
            // body, so compressed responses cannot bypass the decoded-size limit.
            .addInterceptor(ResponseSizeLimitInterceptor())
            .build()
        val api = Retrofit.Builder()
            .baseUrl("$normalizedBaseUrl/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(CpampApi::class.java)
        activeSession = ActiveSession(normalizedBaseUrl, keyDigest, client, api)
        return api
    }

    @Synchronized
    fun invalidate() {
        activeSession?.close()
        activeSession = null
    }

    private data class ActiveSession(
        val baseUrl: String,
        val keyDigest: ByteArray,
        val client: OkHttpClient,
        val api: CpampApi,
    ) {
        fun close() {
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
        }
    }

    private companion object {
        const val PUBLIC_INFO_PATH = "/usage-service/info"
    }
}

class ReadOnlyPathInterceptor : Interceptor {
    private val allowed = setOf(
        "GET /usage-service/info",
        "GET /status",
        "GET /v0/management/dashboard/summary",
        "POST /v0/management/monitoring/analytics",
        "GET /v0/management/auth-files",
        "POST /v0/management/quota-snapshots/query",
        "GET /v0/management/model-prices",
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val operation = "${request.method.uppercase()} ${request.url.encodedPath.trimEnd('/').ifEmpty { "/" }}"
        check(operation in allowed) { "Blocked non-whitelisted CPAMP operation: $operation" }
        return chain.proceed(request)
    }
}

internal class ResponseSizeLimitInterceptor(
    private val maxBytes: Long = 16L * 1024 * 1024,
) : Interceptor {
    init {
        require(maxBytes > 0) { "Response limit must be positive" }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val body = response.body
        val declaredLength = body.contentLength()
        if (declaredLength > maxBytes) {
            body.close()
            throw IOException("CPAMP response exceeds the ${maxBytes}-byte safety limit")
        }
        val limitedSource: BufferedSource = object : ForwardingSource(body.source()) {
            private var received = 0L

            override fun read(sink: Buffer, byteCount: Long): Long {
                val read = super.read(sink, byteCount)
                if (read > 0) {
                    received += read
                    if (received > maxBytes) {
                        body.close()
                        throw IOException("CPAMP response exceeds the ${maxBytes}-byte safety limit")
                    }
                }
                return read
            }
        }.buffer()
        val limitedBody = object : ResponseBody() {
            override fun contentType() = body.contentType()
            override fun contentLength() = declaredLength
            override fun source() = limitedSource
        }
        return response.newBuilder().body(limitedBody).build()
    }
}
