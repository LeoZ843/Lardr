package com.zanoni.lardr.di

import android.content.Context
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
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseDataSource(
        auth: FirebaseAuth,
        firestore: FirebaseFirestore
    ): FirebaseDataSource = FirebaseDataSource(auth, firestore)

    @Provides
    @Singleton
    fun providePreferencesManager(
        @ApplicationContext context: Context
    ): PreferencesManager = PreferencesManager(context)

    @Provides
    @Singleton
    fun provideAuthRepository(
        dataSource: FirebaseDataSource,
        @ApplicationContext context: Context
    ): AuthRepository = AuthRepositoryImpl(dataSource, context)

    @Provides
    @Singleton
    fun provideStoreRepository(
        dataSource: FirebaseDataSource
    ): StoreRepository = StoreRepositoryImpl(dataSource)

    @Provides
    @Singleton
    fun provideUserRepository(
        dataSource: FirebaseDataSource
    ): UserRepository = UserRepositoryImpl(dataSource)
}