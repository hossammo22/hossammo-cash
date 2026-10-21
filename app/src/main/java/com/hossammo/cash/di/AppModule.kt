package com.hossammo.cash.di

import com.hossammo.cash.data.currency.CurrencyService
import com.hossammo.cash.data.currency.RateFetcher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    @Singleton
    abstract fun bindRateFetcher(impl: CurrencyService): RateFetcher
}
