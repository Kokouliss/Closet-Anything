package com.kokouliss.closetanything.core.data.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kokouliss.closetanything.core.data.firebase.FirebaseAuthRepository
import com.kokouliss.closetanything.core.data.firebase.FirestoreHouseholdRepository
import com.kokouliss.closetanything.core.data.firebase.FirestorePlaceRepository
import com.kokouliss.closetanything.core.data.firebase.FirestoreThingRepository
import com.kokouliss.closetanything.core.domain.repository.AuthRepository
import com.kokouliss.closetanything.core.domain.repository.HouseholdRepository
import com.kokouliss.closetanything.core.domain.repository.PlaceRepository
import com.kokouliss.closetanything.core.domain.repository.ThingRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: FirebaseAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindHouseholdRepository(impl: FirestoreHouseholdRepository): HouseholdRepository

    @Binds
    @Singleton
    abstract fun bindPlaceRepository(impl: FirestorePlaceRepository): PlaceRepository

    @Binds
    @Singleton
    abstract fun bindThingRepository(impl: FirestoreThingRepository): ThingRepository

    companion object {
        @Provides
        @Singleton
        fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

        @Provides
        @Singleton
        fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

        @Provides
        @IoDispatcher
        fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
    }
}
