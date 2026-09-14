package com.unipoint.core.di

import android.content.Context
import com.unipoint.data.remote.AdbDataSource
import com.unipoint.data.remote.BluetoothHidDataSource
import com.unipoint.data.remote.NetworkPcDataSource
import com.unipoint.data.repository.ConnectionRepositoryImpl
import com.unipoint.domain.repository.ConnectionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideBluetoothHidDataSource(
        @ApplicationContext context: Context
    ): BluetoothHidDataSource = BluetoothHidDataSource(context)

    @Provides
    @Singleton
    fun provideNetworkPcDataSource(
        @ApplicationContext context: Context
    ): NetworkPcDataSource = NetworkPcDataSource(context)

    @Provides
    @Singleton
    fun provideAdbDataSource(
        @ApplicationContext context: Context
    ): AdbDataSource = AdbDataSource(context)

    @Provides
    @Singleton
    fun provideConnectionRepository(
        bluetooth: BluetoothHidDataSource,
        network: NetworkPcDataSource,
        adb: AdbDataSource
    ): ConnectionRepository = ConnectionRepositoryImpl(bluetooth, network, adb)
}
