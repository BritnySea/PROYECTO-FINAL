package com.univalle.proyectov1.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.univalle.proyectov1.feature.auth.data.repository.AuthRepositoryImpl
import com.univalle.proyectov1.feature.auth.domain.repository.AuthRepository
import com.univalle.proyectov1.feature.dogs.data.remote.WoofApiService
import com.univalle.proyectov1.feature.dogs.data.repository.DogsRepositoryImpl
import com.univalle.proyectov1.feature.dogs.domain.repository.DogsRepository
import com.univalle.proyectov1.feature.user.data.repository.UserRepositoryImpl
import com.univalle.proyectov1.feature.user.domain.repository.UserRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ─── Firebase ─────────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    // ─── Retrofit / OkHttp ───────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideWoofApiService(client: OkHttpClient): WoofApiService {
        // Para emulador: http://10.0.2.2:8000/
        // Para dispositivo real: IP local de la PC en la misma red WiFi (ver con ipconfig)
        return Retrofit.Builder()
            .baseUrl("http://10.0.8.244:8000/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WoofApiService::class.java)
    }

    // ─── Repositories ────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideAuthRepository(
        auth: FirebaseAuth,
        db: FirebaseFirestore
    ): AuthRepository {
        return AuthRepositoryImpl(auth, db)
    }

    @Provides
    @Singleton
    fun provideDogsRepository(
        api: WoofApiService,
        auth: FirebaseAuth,
        db: FirebaseFirestore
    ): DogsRepository {
        return DogsRepositoryImpl(api, auth, db)
    }

    @Provides
    @Singleton
    fun provideUserRepository(
        auth: FirebaseAuth,
        db: FirebaseFirestore
    ): UserRepository {
        return UserRepositoryImpl(auth, db)
    }
}
