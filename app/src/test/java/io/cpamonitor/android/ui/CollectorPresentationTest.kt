package io.cpamonitor.android.ui

import io.cpamonitor.android.data.remote.CollectorDto
import io.cpamonitor.android.data.remote.StatusDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CollectorPresentationTest {
    @Test
    fun missingStatusIsNotReportedAsWaiting() {
        val value = collectorPresentation(MonitorUiState())

        assertEquals(CollectorStateKind.UNKNOWN, value.kind)
        assertEquals("尚未读取状态", value.title)
        assertFalse(value.detail.contains("等待"))
    }

    @Test
    fun loadingStatusIsExplicit() {
        val value = collectorPresentation(MonitorUiState(statusLoading = true))

        assertEquals(CollectorStateKind.LOADING, value.kind)
        assertEquals("正在读取状态", value.title)
    }

    @Test
    fun officialRunningStatusIsHealthy() {
        val value = collectorPresentation(
            MonitorUiState(
                status = StatusDto(
                    events = 12,
                    collector = CollectorDto(collector = "running", transport = "http", mode = "auto"),
                ),
            ),
        )

        assertEquals(CollectorStateKind.RUNNING, value.kind)
        assertEquals("运行正常", value.title)
        assertEquals("HTTP · AUTO", value.detail)
    }

    @Test
    fun startingStoppedAndErrorsRemainDistinct() {
        val starting = collectorPresentation(
            MonitorUiState(status = StatusDto(collector = CollectorDto(collector = "starting"))),
        )
        val stopped = collectorPresentation(
            MonitorUiState(status = StatusDto(collector = CollectorDto(collector = "stopped"))),
        )
        val failed = collectorPresentation(
            MonitorUiState(statusError = "HTTP 503"),
        )

        assertEquals(CollectorStateKind.STARTING, starting.kind)
        assertEquals("正在启动", starting.title)
        assertEquals(CollectorStateKind.STOPPED, stopped.kind)
        assertEquals("已停止", stopped.title)
        assertEquals(CollectorStateKind.ERROR, failed.kind)
        assertEquals("状态读取失败", failed.title)
    }
}
