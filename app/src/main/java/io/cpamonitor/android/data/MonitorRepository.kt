package io.cpamonitor.android.data

import io.cpamonitor.android.data.local.CacheEntity
import io.cpamonitor.android.data.local.MonitorDao
import io.cpamonitor.android.data.local.PreferencesStore
import io.cpamonitor.android.data.local.SecretCipher
import io.cpamonitor.android.data.remote.AnalyticsDto
import io.cpamonitor.android.data.remote.AnalyticsFilters
import io.cpamonitor.android.data.remote.AnalyticsInclude
import io.cpamonitor.android.data.remote.AnalyticsRequest
import io.cpamonitor.android.data.remote.ApiFactory
import io.cpamonitor.android.data.remote.CpampApi
import io.cpamonitor.android.data.remote.DashboardDto
import io.cpamonitor.android.data.remote.EventsPageRequest
import io.cpamonitor.android.data.remote.EventsDto
import io.cpamonitor.android.data.remote.ModelPricesDto
import io.cpamonitor.android.data.remote.QuotaAccountDto
import io.cpamonitor.android.data.remote.QuotaAccountRequest
import io.cpamonitor.android.data.remote.QuotaQueryRequest
import io.cpamonitor.android.data.remote.StatusDto
import io.cpamonitor.android.data.remote.parseAuthFiles
import io.cpamonitor.android.domain.Account
import io.cpamonitor.android.domain.TimeRange
import io.cpamonitor.android.domain.startOfTodayMs
import io.cpamonitor.android.domain.toAccount
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import retrofit2.HttpException

@Serializable
data class AccountBundle(
    val accounts: List<Account> = emptyList(),
    val quotas: List<QuotaAccountDto> = emptyList(),
    val generatedAtMs: Long = 0,
    val quotaEligibleRows: Set<String> = emptySet(),
)

data class CachedValue<T>(val value: T, val updatedAtMs: Long)

class ConnectionException(message: String, cause: Throwable? = null) : Exception(message, cause)

@Singleton
class MonitorRepository @Inject constructor(
    private val preferences: PreferencesStore,
    private val dao: MonitorDao,
    private val apiFactory: ApiFactory,
    private val json: Json,
    private val cipher: SecretCipher,
) {
    private val sessionMutex = Mutex()
    private var sessionGeneration = 0L

    suspend fun validateAndSave(rawUrl: String, adminKey: String) {
        val url = normalizeServerUrl(rawUrl)
        require(adminKey.isNotBlank()) { "请输入 Admin Key" }
        require(adminKey.length <= MAX_ADMIN_KEY_LENGTH) { "Admin Key 长度异常" }
        val normalizedKey = adminKey.trim()
        val api = apiFactory.create(url, normalizedKey)
        try {
            val info = api.info()
            if (info.service.isBlank() || info.mode.isBlank()) {
                throw ConnectionException("该地址不是 CPA-Manager-Plus Manager Server")
            }
            if (!info.configured || info.setupRequired) {
                throw ConnectionException("CPAMP 尚未完成服务器配置")
            }
            if (!info.adminReady) throw ConnectionException("CPAMP 尚未配置管理员凭据")
            api.status()
            val now = System.currentTimeMillis()
            api.analytics(
                AnalyticsRequest(
                    fromMs = now - 60_000,
                    toMs = now,
                    timeZone = ZoneId.systemDefault().id,
                    include = AnalyticsInclude(summary = true),
                ),
            )
        } catch (e: CancellationException) {
            apiFactory.invalidate()
            throw e
        } catch (e: HttpException) {
            apiFactory.invalidate()
            throw when (e.code()) {
                401, 403 -> ConnectionException("Admin Key 无效", e)
                404, 405 -> ConnectionException("缺少监控接口，请升级到 CPAMP v1.12.6 或更高版本", e)
                else -> ConnectionException("服务器返回 HTTP ${e.code()}", e)
            }
        } catch (e: ConnectionException) {
            apiFactory.invalidate()
            throw e
        } catch (e: Exception) {
            apiFactory.invalidate()
            throw ConnectionException(e.message ?: "无法连接服务器", e)
        }
        try {
            sessionMutex.withLock {
                sessionGeneration++
                dao.clearCache()
                dao.clearAlerts()
                preferences.saveConnection(url, normalizedKey)
            }
        } catch (e: Exception) {
            apiFactory.invalidate()
            throw e
        }
    }

    suspend fun disconnect() {
        sessionMutex.withLock {
            sessionGeneration++
            apiFactory.invalidate()
            preferences.clearConnection()
            dao.clearCache()
            dao.clearAlerts()
        }
    }

    suspend fun clearCache() {
        sessionMutex.withLock {
            sessionGeneration++
            dao.clearCache()
        }
    }

    suspend fun status(): StatusDto = session().api.status()

    suspend fun dashboard(now: Instant = Instant.now()): CachedValue<DashboardDto> {
        val session = session()
        val value = session.api.dashboard(
            todayStartMs = startOfTodayMs(now),
            nowMs = now.toEpochMilli(),
        )
        return cache(CACHE_DASHBOARD, DashboardDto.serializer(), value, session.generation)
    }

    suspend fun cachedDashboard(): CachedValue<DashboardDto>? =
        cached(CACHE_DASHBOARD, DashboardDto.serializer())

    suspend fun analytics(
        range: TimeRange,
        filters: AnalyticsFilters = AnalyticsFilters(),
    ): CachedValue<AnalyticsDto> {
        val session = session()
        val value = session.api.analytics(
            AnalyticsRequest(
                fromMs = range.fromMs,
                toMs = range.toMs,
                timeZone = range.zoneId,
                filters = filters,
                include = AnalyticsInclude(
                    summary = true,
                    timeline = true,
                    modelStats = true,
                    accountStats = true,
                    granularity = "auto",
                ),
            ),
        )
        return cache(CACHE_ANALYTICS, AnalyticsDto.serializer(), value, session.generation)
    }

    suspend fun cachedAnalytics(): CachedValue<AnalyticsDto>? =
        cached(CACHE_ANALYTICS, AnalyticsDto.serializer())

    suspend fun events(
        range: TimeRange,
        filters: AnalyticsFilters = AnalyticsFilters(),
        beforeMs: Long? = null,
        beforeId: Long? = null,
    ): EventsDto {
        val session = session()
        return session.api.analytics(
            AnalyticsRequest(
                fromMs = range.fromMs,
                toMs = range.toMs,
                timeZone = range.zoneId,
                filters = filters,
                include = AnalyticsInclude(
                    eventsPage = eventPageRequest(beforeMs, beforeId),
                ),
            ),
        ).events ?: EventsDto()
    }

    suspend fun accountsAndQuotas(nowMs: Long = System.currentTimeMillis()): CachedValue<AccountBundle> {
        val session = session()
        val accounts = parseAuthFiles(session.api.authFiles()).map { it.toAccount() }.distinctBy { it.rowKey }
        // CPAMP rejects the whole request when any account has a provider that its
        // quota service does not understand. Keep those accounts visible, but only
        // ask for snapshots for providers supported by the current quota endpoint.
        val eligibleAccounts = quotaEligibleAccounts(accounts)
        val quotas = quotaBatches(eligibleAccounts).flatMap { batch ->
            if (batch.isEmpty()) return@flatMap emptyList()
            session.api.quotaSnapshots(
                QuotaQueryRequest(
                    accounts = batch.map { account ->
                        QuotaAccountRequest(account.rowKey, account.provider, account.quotaTarget())
                    },
                    nowMs = nowMs,
                ),
            ).items
        }
        val bundle = AccountBundle(accounts, quotas, nowMs, eligibleAccounts.mapTo(linkedSetOf()) { it.rowKey })
        return cache(CACHE_ACCOUNTS, AccountBundle.serializer(), bundle, session.generation)
    }

    suspend fun cachedAccounts(): CachedValue<AccountBundle>? =
        cached(CACHE_ACCOUNTS, AccountBundle.serializer())

    suspend fun modelPrices(): ModelPricesDto = session().api.modelPrices()

    private suspend fun session(): ApiSession = sessionMutex.withLock {
        val config = preferences.connection() ?: run {
            apiFactory.invalidate()
            throw ConnectionException("连接凭据已失效，请重新连接")
        }
        ApiSession(apiFactory.create(config.baseUrl, config.adminKey), sessionGeneration)
    }

    private suspend fun <T> cache(
        key: String,
        serializer: kotlinx.serialization.KSerializer<T>,
        value: T,
        generation: Long,
    ): CachedValue<T> {
        val now = System.currentTimeMillis()
        sessionMutex.withLock {
            if (generation != sessionGeneration) {
                throw CancellationException("Connection changed before cache write")
            }
            val payload = CACHE_ENCRYPTED_PREFIX + cipher.encrypt(json.encodeToString(serializer, value))
            dao.putCache(CacheEntity(key, payload, now))
        }
        return CachedValue(value, now)
    }

    private suspend fun <T> cached(
        key: String,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): CachedValue<T>? {
        val generation = sessionMutex.withLock { sessionGeneration }
        val entity = dao.cache(key) ?: return null
        val plaintext = runCatching {
            if (entity.payload.startsWith(CACHE_ENCRYPTED_PREFIX)) {
                cipher.decrypt(entity.payload.removePrefix(CACHE_ENCRYPTED_PREFIX))
            } else {
                entity.payload
            }
        }.getOrNull() ?: return null
        val value = runCatching {
            json.decodeFromString(serializer, plaintext)
        }.getOrNull() ?: return null
        if (!entity.payload.startsWith(CACHE_ENCRYPTED_PREFIX)) {
            runCatching {
                sessionMutex.withLock {
                    if (generation == sessionGeneration && dao.cache(key)?.payload == entity.payload) {
                        val encrypted = CACHE_ENCRYPTED_PREFIX + cipher.encrypt(plaintext)
                        dao.putCache(entity.copy(payload = encrypted))
                    }
                }
            }
        }
        return CachedValue(value, entity.updatedAtMs)
    }

    private data class ApiSession(val api: CpampApi, val generation: Long)

    private companion object {
        const val MAX_ADMIN_KEY_LENGTH = 4096
        const val CACHE_ENCRYPTED_PREFIX = "encrypted:"
        const val CACHE_DASHBOARD = "dashboard"
        const val CACHE_ANALYTICS = "analytics"
        const val CACHE_ACCOUNTS = "accounts"
    }
}

internal fun <T> quotaBatches(accounts: List<T>): List<List<T>> = accounts.chunked(200)

internal fun quotaEligibleAccounts(accounts: List<Account>): List<Account> = accounts.filter {
    normalizeQuotaProvider(it.provider) in SUPPORTED_QUOTA_PROVIDERS
}

private fun normalizeQuotaProvider(value: String): String = when (
    val normalized = value.trim().lowercase().replace('_', '-')
) {
    "x-ai", "grok" -> "xai"
    else -> normalized
}

private val SUPPORTED_QUOTA_PROVIDERS = setOf("codex", "claude", "antigravity", "kimi", "xai")

internal fun eventPageRequest(beforeMs: Long?, beforeId: Long?) = EventsPageRequest(
    limit = 50,
    beforeMs = beforeMs,
    beforeId = beforeId,
)

fun normalizeServerUrl(raw: String): String {
    require(raw.length <= 2048) { "服务器地址长度异常" }
    val candidate = raw.trim().let { if ("://" in it) it else "https://$it" }
    val url = candidate.toHttpUrlOrNull() ?: throw IllegalArgumentException("服务器地址格式无效")
    require(url.scheme == "https") { "仅支持 HTTPS 服务器" }
    require(url.username.isEmpty() && url.password.isEmpty()) { "服务器地址不能包含用户信息" }
    require(url.query == null && url.fragment == null) { "服务器地址不能包含查询参数或片段" }
    require(url.encodedPath == "/") { "请输入服务器根域名，不要附加路径" }
    return url.newBuilder().encodedPath("/").build().toString().trimEnd('/')
}
