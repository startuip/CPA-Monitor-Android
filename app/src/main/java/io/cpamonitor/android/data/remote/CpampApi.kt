package io.cpamonitor.android.data.remote

import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface CpampApi {
    @GET("usage-service/info")
    suspend fun info(): ServerInfoDto

    @GET("status")
    suspend fun status(): StatusDto

    @GET("v0/management/dashboard/summary")
    suspend fun dashboard(
        @Query("today_start_ms") todayStartMs: Long,
        @Query("now_ms") nowMs: Long,
        @Query("top_models") topModels: Int = 5,
        @Query("recent_failures") recentFailures: Int = 5,
    ): DashboardDto

    @POST("v0/management/monitoring/analytics")
    suspend fun analytics(@Body request: AnalyticsRequest): AnalyticsDto

    @GET("v0/management/auth-files")
    suspend fun authFiles(): JsonElement

    @POST("v0/management/quota-snapshots/query")
    suspend fun quotaSnapshots(@Body request: QuotaQueryRequest): QuotaQueryDto

    @GET("v0/management/model-prices")
    suspend fun modelPrices(): ModelPricesDto
}
