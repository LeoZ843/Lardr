package com.zanoni.lardr.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.zanoni.lardr.data.local.PreferencesManager
import com.zanoni.lardr.data.remote.FirebaseDataSource
import com.zanoni.lardr.data.repository.AuthRepository
import com.zanoni.lardr.data.repository.AuthRepositoryImpl
import com.zanoni.lardr.data.repository.StoreRepository
import com.zanoni.lardr.data.repository.StoreRepositoryImpl
import com.zanoni.lardr.data.repository.UserRepository
import com.zanoni.lardr.data.repository.UserRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        return FirebaseFirestore.getInstance()
    }

    @Provides
    @Singleton
    fun provideFirebaseDataSource(
        auth: FirebaseAuth,
        firestore: FirebaseFirestore
    ): FirebaseDataSource {
        return FirebaseDataSource(auth, firestore)
    }

    @Provides
    @Singleton
    fun providePreferencesManager(
        @ApplicationContext context: Context
    ): PreferencesManager {
        return PreferencesManager(context)
    }

    @Provides
    @Singleton
    fun provideAuthRepository(
        dataSource: FirebaseDataSource,
        @ApplicationContext context: Context
    ): AuthRepository {
        return AuthRepositoryImpl(dataSource, context)
    }

    @Provides
    @Singleton
    fun provideStoreRepository(
        dataSource: FirebaseDataSource
    ): StoreRepository {
        return StoreRepositoryImpl(dataSource)
    }

    @Provides
    @Singleton
    fun provideUserRepository(
        dataSource: FirebaseDataSource
    ): UserRepository {
        return UserRepositoryImpl(dataSource)
    }
}