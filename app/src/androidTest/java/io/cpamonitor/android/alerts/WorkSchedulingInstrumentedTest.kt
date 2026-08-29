package io.cpamonitor.android.alerts

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkSchedulingInstrumentedTest {
    @Test
    fun schedulesOnePeriodicWorker() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runCatching {
            WorkManagerTestInitHelper.initializeTestWorkManager(
                context,
                Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
            )
        }
        MonitorWorker.schedule(context)
        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork("cpa-monitor-alert-check")
            .get(5, TimeUnit.SECONDS)
        assertEquals(1, infos.size)
    }
}

