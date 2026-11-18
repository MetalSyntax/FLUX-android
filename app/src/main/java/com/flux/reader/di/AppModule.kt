package com.flux.reader.di

import android.content.Context
import androidx.room.Room
import com.flux.reader.data.local.FluxDatabase
import com.flux.reader.data.local.dao.FluxDao
import com.flux.reader.data.repository.BookRepositoryImpl
import com.flux.reader.data.repository.DiscoverRepositoryImpl
import com.flux.reader.data.repository.SettingsRepositoryImpl
import com.flux.reader.domain.repository.BookRepository
import com.flux.reader.domain.repository.DiscoverRepository
import com.flux.reader.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkAndDatabaseModule {

    @Provides
    @Singleton
    fun provideFluxDatabase(@ApplicationContext context: Context): FluxDatabase =
        Room.databaseBuilder(context, FluxDatabase::class.java, "flux_reader.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideFluxDao(db: FluxDatabase): FluxDao = db.fluxDao()

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 45_000
        }
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                }
            )
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryBindingModule {

    @Binds
    @Singleton
    abstract fun bindBookRepository(impl: BookRepositoryImpl): BookRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindDiscoverRepository(impl: DiscoverRepositoryImpl): DiscoverRepository
}
