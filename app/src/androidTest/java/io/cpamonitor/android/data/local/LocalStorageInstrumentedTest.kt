package io.cpamonitor.android.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.cpamonitor.android.data.MonitorRepository
import io.cpamonitor.android.data.remote.ApiFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalStorageInstrumentedTest {
    private lateinit var database: MonitorDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MonitorDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After fun tearDown() = database.close()

    @Test
    fun cacheRestoresAndClears() = runBlocking {
        database.monitorDao().putCache(CacheEntity("summary", "{}", 42))
        assertEquals(42L, database.monitorDao().cache("summary")?.updatedAtMs)
        database.monitorDao().clearCache()
        assertNull(database.monitorDao().cache("summary"))
    }

    @Test
    fun keystoreRoundTripAndInvalidation() {
        val cipher = KeystoreCipher()
        val encrypted = cipher.encrypt("admin-secret")
        assertEquals("admin-secret", cipher.decrypt(encrypted))
        cipher.deleteKey()
    }

    @Test
    fun legacyPlaintextCacheIsMigratedToEncryptedPayload() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cipher = object : SecretCipher {
            override fun encrypt(plaintext: String) = "test:${plaintext.reversed()}"
            override fun decrypt(blob: String) = blob.removePrefix("test:").reversed()
            override fun deleteKey() = Unit
        }
        val json = Json { ignoreUnknownKeys = true }
        database.monitorDao().putCache(CacheEntity("dashboard", "{}", 42))
        val repository = MonitorRepository(
            PreferencesStore(context, cipher),
            database.monitorDao(),
            ApiFactory(json),
            json,
            cipher,
        )

        assertEquals(42L, repository.cachedDashboard()?.updatedAtMs)
        assertEquals("encrypted:test:}{", database.monitorDao().cache("dashboard")?.payload)
    }
}
