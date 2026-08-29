package io.cpamonitor.android.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class ServerInfoDto(
    val service: String = "",
    val mode: String = "",
    val configured: Boolean = false,
    val adminReady: Boolean = false,
    val setupRequired: Boolean = false,
)

@Serializable
data class StatusDto(
    val service: String = "",
    val events: Long = 0,
    val deadLetters: Long = 0,
    val collector: CollectorDto = CollectorDto(),
)

@Serializable
data class CollectorDto(
    val collector: String = "unknown",
    val transport: String = "",
    val mode: String = "",
    val lastConsumedAt: Long = 0,
    val lastInsertedAt: Long = 0,
    val totalInserted: Long = 0,
    val lastError: String = "",
)

@Serializable
data class DashboardDto(
    @SerialName("generated_at_ms") val generatedAtMs: Long = 0,
    val today: UsageSummaryDto = UsageSummaryDto(),
    @SerialName("rolling_30m") val rolling30m: RollingDto = RollingDto(),
    @SerialName("top_models_today") val topModels: List<ModelStatDto> = emptyList(),
    @SerialName("traffic_timeline") val traffic: List<TimelinePointDto> = emptyList(),
    @SerialName("recent_failures") val recentFailures: List<FailureDto> = emptyList(),
)

@Serializable
data class UsageSummaryDto(
    @SerialName("total_calls") val totalCalls: Long = 0,
    @SerialName("success_calls") val successCalls: Long = 0,
    @SerialName("failure_calls") val failureCalls: Long = 0,
    @SerialName("success_rate") val successRate: Double = 0.0,
    @SerialName("input_tokens") val inputTokens: Long = 0,
    @SerialName("output_tokens") val outputTokens: Long = 0,
    @SerialName("cached_tokens") val cachedTokens: Long = 0,
    @SerialName("total_tokens") val totalTokens: Long = 0,
    @SerialName("total_cost") val totalCost: Double = 0.0,
    @SerialName("average_latency_ms") val averageLatencyMs: Double? = null,
    @SerialName("rpm_30m") val rpm30m: Double = 0.0,
    @SerialName("tpm_30m") val tpm30m: Double = 0.0,
)

@Serializable
data class RollingDto(
    val rpm: Double = 0.0,
    val tpm: Double = 0.0,
    @SerialName("total_calls") val totalCalls: Long = 0,
    @SerialName("total_tokens") val totalTokens: Long = 0,
)

@Serializable
data class TimelinePointDto(
    @SerialName("bucket_ms") val bucketMs: Long = 0,
    val label: String = "",
    val calls: Long = 0,
    val tokens: Long = 0,
    val success: Long = 0,
    val failure: Long = 0,
    @SerialName("total_tokens") val totalTokens: Long = 0,
    val cost: Double = 0.0,
    @SerialName("success_rate") val successRate: Double = 0.0,
)

@Serializable
data class ModelStatDto(
    val model: String = "未知模型",
    val calls: Long = 0,
    @SerialName("success_calls") val successCalls: Long = 0,
    @SerialName("failure_calls") val failureCalls: Long = 0,
    @SerialName("success_rate") val successRate: Double = 0.0,
    val tokens: Long = 0,
    @SerialName("total_tokens") val totalTokens: Long = 0,
    val cost: Double = 0.0,
)

@Serializable
data class AccountStatDto(
    val id: String = "",
    @SerialName("account_snapshot") val account: String = "",
    @SerialName("auth_label_snapshot") val label: String = "",
    @SerialName("auth_provider_snapshot") val provider: String = "unknown",
    @SerialName("auth_indices") val authIndices: List<String> = emptyList(),
    val calls: Long = 0,
    @SerialName("success_calls") val successCalls: Long = 0,
    @SerialName("failure_calls") val failureCalls: Long = 0,
    @SerialName("success_rate") val successRate: Double = 0.0,
    @SerialName("total_tokens") val totalTokens: Long = 0,
    val cost: Double = 0.0,
    @SerialName("average_latency_ms") val averageLatencyMs: Double? = null,
    @SerialName("last_seen_ms") val lastSeenMs: Long = 0,
)

@Serializable
data class FailureDto(
    @SerialName("timestamp_ms") val timestampMs: Long = 0,
    val model: String = "未知模型",
    @SerialName("account_snapshot") val account: String = "",
    @SerialName("auth_label_snapshot") val label: String = "",
    @SerialName("auth_provider_snapshot") val provider: String = "unknown",
    val endpoint: String = "",
    @SerialName("duration_ms") val durationMs: Long? = null,
    @SerialName("fail_status_code") val statusCode: Long? = null,
    @SerialName("fail_summary") val summary: String = "",
)

@Serializable
data class EventDto(
    @SerialName("event_hash") val eventHash: String = "",
    @SerialName("timestamp_ms") val timestampMs: Long = 0,
    val model: String = "未知模型",
    val endpoint: String = "",
    val method: String = "",
    val path: String = "",
    @SerialName("account_snapshot") val account: String = "",
    @SerialName("auth_label_snapshot") val label: String = "",
    @SerialName("auth_provider_snapshot") val provider: String = "unknown",
    @SerialName("auth_index") val authIndex: String = "",
    @SerialName("input_tokens") val inputTokens: Long = 0,
    @SerialName("output_tokens") val outputTokens: Long = 0,
    @SerialName("cached_tokens") val cachedTokens: Long = 0,
    @SerialName("total_tokens") val totalTokens: Long = 0,
    @SerialName("latency_ms") val latencyMs: Long? = null,
    val failed: Boolean = false,
    @SerialName("fail_status_code") val statusCode: Long? = null,
    @SerialName("fail_summary") val failSummary: String = "",
)

@Serializable
data class EventsDto(
    val items: List<EventDto> = emptyList(),
    @SerialName("next_before_ms") val nextBeforeMs: Long = 0,
    @SerialName("next_before_id") val nextBeforeId: Long = 0,
    @SerialName("has_more") val hasMore: Boolean = false,
    @SerialName("total_count") val totalCount: Long = 0,
)

@Serializable
data class AnalyticsDto(
    @SerialName("generated_at_ms") val generatedAtMs: Long = 0,
    val summary: UsageSummaryDto? = null,
    val timeline: List<TimelinePointDto> = emptyList(),
    @SerialName("model_stats") val modelStats: List<ModelStatDto> = emptyList(),
    @SerialName("account_stats") val accountStats: List<AccountStatDto> = emptyList(),
    @SerialName("recent_failures") val recentFailures: List<FailureDto> = emptyList(),
    val events: EventsDto? = null,
)

@Serializable
data class AnalyticsRequest(
    @SerialName("from_ms") val fromMs: Long,
    @SerialName("to_ms") val toMs: Long,
    @SerialName("now_ms") val nowMs: Long = toMs,
    @SerialName("time_zone") val timeZone: String,
    val filters: AnalyticsFilters = AnalyticsFilters(),
    val include: AnalyticsInclude,
)

@Serializable
data class AnalyticsFilters(
    val models: List<String> = emptyList(),
    val providers: List<String> = emptyList(),
    val accounts: List<String> = emptyList(),
    @SerialName("credential_ids") val credentialIds: List<String> = emptyList(),
    @SerialName("failed_only") val failedOnly: Boolean = false,
)

@Serializable
data class AnalyticsInclude(
    val summary: Boolean = false,
    val timeline: Boolean = false,
    @SerialName("model_stats") val modelStats: Boolean = false,
    @SerialName("account_stats") val accountStats: Boolean = false,
    @SerialName("credential_stats") val credentialStats: Boolean = false,
    @SerialName("recent_failures") val recentFailures: Int = 0,
    @SerialName("events_page") val eventsPage: EventsPageRequest? = null,
    val granularity: String = "auto",
)

@Serializable
data class EventsPageRequest(
    val limit: Int = 50,
    @SerialName("before_ms") val beforeMs: Long? = null,
    @SerialName("before_id") val beforeId: Long? = null,
)

internal fun parseAuthFiles(payload: JsonElement): List<JsonObject> = when (payload) {
    is JsonArray -> payload.mapNotNull { it as? JsonObject }
    is JsonObject -> {
        val nested = listOf("auth_files", "authFiles", "files", "items", "data")
            .firstNotNullOfOrNull { key -> payload[key] }
        when {
            nested != null -> parseAuthFiles(nested)
            payload.keys.any { it in setOf("name", "file_name", "fileName", "id") } -> listOf(payload)
            else -> emptyList()
        }
    }
    else -> emptyList()
}

@Serializable
data class QuotaQueryRequest(
    val accounts: List<QuotaAccountRequest>,
    @SerialName("now_ms") val nowMs: Long,
    @SerialName("include_inactive") val includeInactive: Boolean = false,
)

@Serializable
data class QuotaAccountRequest(
    @SerialName("row_key") val rowKey: String,
    val provider: String,
    val account: QuotaTargetDto,
)

@Serializable
data class QuotaTargetDto(
    @SerialName("account_snapshot") val accountSnapshot: String = "",
    @SerialName("auth_label_snapshot") val authLabelSnapshot: String = "",
    @SerialName("auth_file_snapshot") val authFileSnapshot: String = "",
    @SerialName("auth_provider_snapshot") val authProviderSnapshot: String = "",
    @SerialName("auth_account_id_snapshot") val authAccountIdSnapshot: String = "",
    @SerialName("auth_project_id_snapshot") val authProjectIdSnapshot: String = "",
    @SerialName("auth_index") val authIndex: String = "",
    val source: String = "",
)

@Serializable
data class QuotaQueryDto(
    @SerialName("generated_at_ms") val generatedAtMs: Long = 0,
    val items: List<QuotaAccountDto> = emptyList(),
)

@Serializable
data class QuotaAccountDto(
    @SerialName("row_key") val rowKey: String = "",
    @SerialName("account_key") val accountKey: String = "",
    val provider: String = "unknown",
    val windows: List<QuotaWindowDto> = emptyList(),
)

@Serializable
data class QuotaWindowDto(
    @SerialName("provider_window_id") val id: String = "",
    @SerialName("window_kind") val kind: String = "unknown",
    @SerialName("window_mode") val mode: String = "unknown",
    @SerialName("model_scope_kind") val scopeKind: String = "all",
    @SerialName("model_scope_key") val scopeKey: String = "",
    @SerialName("observed_at_ms") val observedAtMs: Long = 0,
    @SerialName("cycle_end_ms") val cycleEndMs: Long? = null,
    @SerialName("remaining_percent") val remainingPercent: Double? = null,
    @SerialName("used_percent") val usedPercent: Double? = null,
    @SerialName("used_value") val usedValue: Double? = null,
    @SerialName("limit_value") val limitValue: Double? = null,
    @SerialName("quota_unit") val unit: String = "",
    val stale: Boolean = false,
    val availability: String = "",
    @SerialName("plan_type") val planType: String = "",
)

@Serializable
data class ModelPricesDto(val prices: Map<String, ModelPriceDto> = emptyMap())

@Serializable
data class ModelPriceDto(
    val prompt: Double = 0.0,
    val completion: Double = 0.0,
    val cache: Double = 0.0,
)
