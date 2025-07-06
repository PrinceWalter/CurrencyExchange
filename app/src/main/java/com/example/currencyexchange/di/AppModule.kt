package com.example.currencyexchange.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import com.example.currencyexchange.data.database.CurrencyExchangeDatabase
import com.example.currencyexchange.data.dao.*

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideCurrencyExchangeDatabase(@ApplicationContext context: Context): CurrencyExchangeDatabase {
        return CurrencyExchangeDatabase.getDatabase(context)
    }

    @Provides
    fun providePartnerDao(database: CurrencyExchangeDatabase): PartnerDao {
        return database.partnerDao()
    }

    @Provides
    fun provideTransactionDao(database: CurrencyExchangeDatabase): TransactionDao {
        return database.transactionDao()
    }

    @Provides
    fun provideExchangeRateDao(database: CurrencyExchangeDatabase): ExchangeRateDao {
        return database.exchangeRateDao()
    }
}