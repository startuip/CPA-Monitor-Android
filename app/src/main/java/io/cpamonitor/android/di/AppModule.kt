package io.cpamonitor.android.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import io.cpamonitor.android.data.local.KeystoreCipher
import io.cpamonitor.android.data.local.MonitorDao
import io.cpamonitor.android.data.local.MonitorDatabase
import io.cpamonitor.android.data.local.SecretCipher
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
abstract class CipherModule {
    @Binds
    @Singleton
    abstract fun bindSecretCipher(value: KeystoreCipher): SecretCipher
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = false
    }

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): MonitorDatabase =
        Room.databaseBuilder(context, MonitorDatabase::class.java, "cpa_monitor.db").build()

    @Provides
    fun dao(database: MonitorDatabase): MonitorDao = database.monitorDao()
}

